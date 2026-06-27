const app = getApp();
const { lockApi, recordApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

Page({
  data: {
    mode: 'control',
    lockId: null,
    lockInfo: {},
    devices: [],
    selectedDevice: null,
    lockName: '',
    scanning: false,
    scanStatus: 'idle'
  },

  onLoad(options) {
    if (options.mode === 'add') {
      this.setData({ mode: 'search' });
      this.checkBluetoothAndScan();
    } else if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
      this.loadLockDetail();
    }
  },

  checkBluetoothAndScan() {
    ttlock.openBluetoothAdapter()
      .then(() => {
        this.startScan();
      })
      .catch((err) => {
        console.error('Bluetooth adapter open failed:', err);
        if (err.errCode === 10001) {
          wx.showToast({ title: '请打开蓝牙', icon: 'none' });
          this.setData({ scanStatus: 'no_bluetooth' });
        } else {
          wx.showToast({ title: err.errMsg || '蓝牙初始化失败', icon: 'none' });
          this.setData({ scanStatus: 'error' });
        }
      });
  },

  async loadLockDetail() {
    try {
      wx.showLoading({ title: '加载中...' });
      const lockInfo = await lockApi.getDetail(this.data.lockId);
      this.setData({ lockInfo });
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      console.error('Load lock detail failed:', err);
    }
  },

  async onUnlock() {
    const { lockInfo, lockId } = this.data;

    try {
      wx.showLoading({ title: '获取开锁数据...' });

      const unlockData = await lockApi.getUnlockData(lockId);
      const freshLockData = unlockData.lockData;
      if (!freshLockData) {
        wx.hideLoading();
        wx.showToast({ title: '锁数据不可用', icon: 'none' });
        return;
      }

      this.setData({
        'lockInfo.lockData': freshLockData,
        'lockInfo.electricQuantity': unlockData.electricQuantity
      });

      wx.showLoading({ title: '开锁中...' });

      const result = await ttlock.unlock(freshLockData);
      if (result.errorCode !== 0) {
        wx.hideLoading();
        wx.showToast({ title: '开锁失败', icon: 'none' });
        return;
      }

      wx.showToast({ title: '已开锁', icon: 'success' });

      wx.showLoading({ title: '读取操作记录...' });
      try {
        const logResult = await Promise.race([
          ttlock.getOperationLog(freshLockData, 2),
          new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 8000))
        ]);
        if (logResult && logResult.log) {
          recordApi.sync(lockId, logResult.log).catch(() => {});
        }
      } catch (e) {
        console.error('Get operation log failed:', e);
      }
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      console.error('Unlock error:', err);
      wx.showToast({ title: '开锁失败', icon: 'none' });
    } finally {
      ttlock.finishOperations().catch(() => {});
    }
  },

  async onLongPressLock() {
    const { lockId } = this.data;

    try {
      wx.showLoading({ title: '获取开锁数据...' });

      const unlockData = await lockApi.getUnlockData(lockId);
      const freshLockData = unlockData.lockData;
      if (!freshLockData) {
        wx.hideLoading();
        wx.showToast({ title: '锁数据不可用', icon: 'none' });
        return;
      }

      wx.showLoading({ title: '闭锁中...' });

      const result = await ttlock.lock(freshLockData);
      if (result.errorCode !== 0) {
        wx.hideLoading();
        wx.showToast({ title: '闭锁失败', icon: 'none' });
        return;
      }

      wx.showToast({ title: '已闭锁', icon: 'success' });
      recordApi.upload({ lockId, action: 'lock', recordTime: Date.now() }).catch(() => {});
    } catch (err) {
      wx.hideLoading();
      console.error('Lock error:', err);
      wx.showToast({ title: '闭锁失败', icon: 'none' });
    } finally {
      ttlock.finishOperations().catch(() => {});
    }
  },

  startScan() {
    this.setData({ scanning: true, scanStatus: 'scanning', devices: [] });

    // 插件回调里的 deviceFromScanList 已按"添加状态 + 信号强度"排序，
    // 这里整表替换，保留官方排序，同时让 isSettingMode/rssi/电量 的后续变化能被反映出来。
    ttlock.startSearch(
      (_device, list) => {
        if (this.data.mode !== 'search') return;
        this.setData({
          devices: list,
          scanStatus: list.length > 0 ? 'found' : 'scanning'
        });
      },
      (err) => {
        console.error('Scan failed:', err);
        // 3.1.0 后未扫描到任意设备也会回调，但插件不会关闭扫描，
        // 因此这里仅在尚未发现设备时把状态切到提示态，避免清空已经发现的列表。
        if (this.data.devices.length === 0) {
          this.setData({ scanStatus: 'scanning' });
        }
      }
    );
  },

  onStopSearch() {
    ttlock.stopSearch().catch(() => {});
    wx.navigateBack();
  },

  onSelectDevice(e) {
    const index = e.currentTarget.dataset.index;
    const selectedDevice = this.data.devices[index];
    if (!selectedDevice) return;

    // 对齐官方 demo：已被初始化的锁不进入确认页，提示用户先重置。
    if (!selectedDevice.isSettingMode) {
      wx.showToast({ title: '该智能锁已被初始化，请先重置', icon: 'none' });
      return;
    }

    // 进入 confirm 前停止扫描，避免初始化期间蓝牙资源被扫描占用。
    ttlock.stopSearch().catch(() => {});
    this.setData({
      selectedDevice,
      lockName: selectedDevice.lockName || '',
      mode: 'confirm'
    });
  },

  onLockNameInput(e) {
    this.setData({ lockName: e.detail.value });
  },

  async onConfirmAdd() {
    const { selectedDevice, lockName } = this.data;
    if (!selectedDevice) return;

    let initResult = null;
    try {
      wx.showLoading({ title: '初始化中...', mask: true });
      initResult = await ttlock.initLock(selectedDevice);
    } catch (err) {
      wx.hideLoading();
      console.error('initLock failed:', err);
      wx.showToast({ title: err.message || '蓝牙初始化失败', icon: 'none' });
      ttlock.finishOperations().catch(() => {});
      return;
    }

    try {
      wx.showLoading({ title: '同步到云端...', mask: true });
      await lockApi.add({
        lockData: initResult.lockData,
        lockName: lockName || selectedDevice.lockName,
        // 锁编号：把用户起的名字同步到 TTLock 云端 alias，
        // 这样"基本信息"页能直接展示云端返回的 lockAlias，而不是 lockName 的回退。
        lockAlias: lockName || selectedDevice.lockName,
        lockMac: selectedDevice.lockMac
      });

      wx.hideLoading();
      wx.showToast({ title: '添加成功', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 1000);
    } catch (err) {
      wx.hideLoading();
      console.error('Add lock failed on server:', err);
      // lockData 已经下发到锁但服务端没保存成功，按官方 demo 兜底重置物理锁，
      // 否则用户除了长按重置键 + 输入 000# 之外没法再添加这把锁。
      try {
        wx.showLoading({ title: '回滚中...', mask: true });
        await ttlock.resetLock(initResult.lockData);
        wx.hideLoading();
      } catch (resetErr) {
        wx.hideLoading();
        console.error('Reset lock after add failure also failed:', resetErr);
      }
      const tip = (err && err.message) || '添加失败，请重试';
      wx.showToast({ title: tip, icon: 'none' });
    } finally {
      ttlock.finishOperations().catch(() => {});
    }
  },

  goKeyList() {
    wx.navigateTo({
      url: `/pages/key-list/key-list?lockId=${this.data.lockId}&lockName=${encodeURIComponent(this.data.lockInfo.lockName || '')}`
    });
  },

  goAdminList() {
    wx.navigateTo({
      url: `/pages/admin-list/admin-list?lockId=${this.data.lockId}&lockName=${encodeURIComponent(this.data.lockInfo.lockName || '')}`
    });
  },

  goICCardList() {
    const info = this.data.lockInfo || {};
    wx.navigateTo({
      url: `/pages/iccard-list/iccard-list?lockId=${this.data.lockId}` +
           `&lockName=${encodeURIComponent(info.lockName || '')}` +
           // 把 admin 自己的 ekey 有效期透传，子页面用来在 picker 上做上限提示
           `&keyType=${info.keyType || 'common'}` +
           `&ekeyStartDate=${info.ekeyStartDate || 0}` +
           `&ekeyEndDate=${info.ekeyEndDate || 0}`
    });
  },

  goFingerprintList() {
    const info = this.data.lockInfo || {};
    wx.navigateTo({
      url: `/pages/fingerprint-list/fingerprint-list?lockId=${this.data.lockId}` +
           `&lockName=${encodeURIComponent(info.lockName || '')}` +
           `&keyType=${info.keyType || 'common'}` +
           `&ekeyStartDate=${info.ekeyStartDate || 0}` +
           `&ekeyEndDate=${info.ekeyEndDate || 0}`
    });
  },

  goPwdList() {
    wx.navigateTo({
      url: `/pages/pwd-list/pwd-list?lockId=${this.data.lockId}`
    });
  },

  goRecordList() {
    wx.navigateTo({
      url: `/pages/record-list/record-list?lockId=${this.data.lockId}`
    });
  },

  goSettings() {
    wx.navigateTo({
      url: `/pages/lock-settings/lock-settings?lockId=${this.data.lockId}&lockName=${encodeURIComponent(this.data.lockInfo.lockName || '')}`
    });
  },

  noop() {},

  onUnload() {
    ttlock.stopAllOperations().catch(() => {});
  }
});
