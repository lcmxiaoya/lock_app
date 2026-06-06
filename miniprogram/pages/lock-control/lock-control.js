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
    scanStatus: 'idle',
    showDeleteModal: false,
    deletePassword: ''
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
    this.setData({ scanning: true, scanStatus: 'scanning' });

    ttlock.startSearch((lockDevice) => {
      const devices = this.data.devices;
      const exists = devices.find(d => d.deviceId === lockDevice.deviceId);
      if (!exists) {
        devices.push(lockDevice);
        this.setData({ devices, scanStatus: 'found' });
      }
    });
  },

  onStopSearch() {
    ttlock.stopSearch();
    wx.navigateBack();
  },

  onSelectDevice(e) {
    const index = e.currentTarget.dataset.index;
    const selectedDevice = this.data.devices[index];
    this.setData({ 
      selectedDevice,
      mode: 'confirm'
    });
  },

  onLockNameInput(e) {
    this.setData({ lockName: e.detail.value });
  },

  async onConfirmAdd() {
    const { selectedDevice, lockName } = this.data;
    
    try {
      wx.showLoading({ title: '添加中...' });
      
      const initResult = await ttlock.initLock(selectedDevice);
      
      const result = await lockApi.add({
        lockData: initResult.lockData,
        lockName: lockName || selectedDevice.lockName,
        lockMac: selectedDevice.lockMac
      });
      
      wx.hideLoading();
      wx.showToast({ title: '添加成功', icon: 'success' });

      setTimeout(() => {
        wx.navigateBack();
      }, 1000);
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: '添加失败', icon: 'none' });
      console.error('Add lock failed:', err);
    } finally {
      ttlock.finishOperations().catch(() => {});
    }
  },

  goKeyList() {
    wx.navigateTo({
      url: `/pages/key-list/key-list?lockId=${this.data.lockId}&lockName=${encodeURIComponent(this.data.lockInfo.lockName || '')}`
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

  onDeleteLock() {
    this.setData({ showDeleteModal: true, deletePassword: '' });
  },

  onPasswordInput(e) {
    this.setData({ deletePassword: e.detail.value });
  },

  onCancelDelete() {
    this.setData({ showDeleteModal: false, deletePassword: '' });
  },

  async onConfirmDelete() {
    const { deletePassword, lockId, lockInfo } = this.data;
    
    if (!deletePassword) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }

    if (!lockInfo.lockData) {
      wx.showToast({ title: '锁数据不可用', icon: 'none' });
      return;
    }
    
    try {
      wx.showLoading({ title: '正在重置锁...' });
      
      await ttlock.resetLock(lockInfo.lockData);
      
      wx.showLoading({ title: '正在删除...' });
      
      await lockApi.delete(lockId, deletePassword);
      
      this.setData({ showDeleteModal: false, deletePassword: '' });
      wx.hideLoading();
      wx.showToast({ title: '删除成功', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 1000);
    } catch (err) {
      wx.hideLoading();
      console.error('Delete lock failed:', err);
      wx.showToast({ title: err.errMsg || '删除失败', icon: 'none' });
    } finally {
      ttlock.finishOperations().catch(() => {});
    }
  },

  noop() {},

  onUnload() {
    ttlock.stopSearch();
  }
});
