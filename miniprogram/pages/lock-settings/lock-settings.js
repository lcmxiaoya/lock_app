const app = getApp();
const { lockApi, recordApi, keyApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

Page({
  data: {
    lockId: null,
    lockName: '',
    lockAlias: '',
    lockMac: '',
    ttLockId: null,
    electricQuantity: 0,
    keyType: 'owner',
    ekeyStartDate: 0,
    ekeyEndDate: 0,
    groupId: 0,

    // 派生显示
    macIdText: '-',
    validityText: '永久',
    groupText: '未分组',

    // 上传/刷新状态
    uploading: false,
    refreshingBattery: false,
    lastSyncText: '',

    // 名称编辑弹窗
    showNameModal: false,
    editingName: '',
    savingName: false,

    // 删除锁弹窗
    ekeyRecordId: null,
    showDeleteModal: false,
    deleteConfirmText: '',
    deletingLock: false,

    // 蓝牙重置锁时需要的 lockData（initLock 初始化时下发到锁端的那串）
    lockData: ''
  },

  onLoad(options) {
    if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
    }
    if (options.lockName) {
      this.setData({ lockName: decodeURIComponent(options.lockName) });
      wx.setNavigationBarTitle({ title: `${this.data.lockName} · 设置` });
    }
    this.loadLockDetail();
  },

  onShow() {
    // 从设置页返回后重新拉取，确保电池、有效期等数据最新
    if (this.data.lockId) {
      this.loadLockDetail();
    }
  },

  async loadLockDetail() {
    if (!this.data.lockId) return;
    try {
      const data = await lockApi.getDetail(this.data.lockId);
      const startDate = data.ekeyStartDate || 0;
      const endDate = data.ekeyEndDate || 0;
      this.setData({
        lockName: data.lockName || this.data.lockName,
        lockAlias: data.lockAlias || '',
        lockMac: data.lockMac || '',
        ttLockId: data.lockId || null,
        electricQuantity: data.electricQuantity || 0,
        keyType: data.keyType || 'common',
        ekeyRecordId: data.ekeyRecordId || null,
        ekeyStartDate: startDate,
        ekeyEndDate: endDate,
        groupId: data.groupId || 0,
        // 物理重置锁时要用，缺失时蓝牙 resetLock 会拒绝
        lockData: data.lockData || '',
        macIdText: this.formatMacId(data.lockMac, data.lockId),
        validityText: this.formatValidity(startDate, endDate),
        groupText: (data.groupId && data.groupId > 0) ? '已分组' : '未分组'
      });
    } catch (err) {
      console.error('Load lock detail failed:', err);
    }
  },

  formatMacId(mac, ttLockId) {
    if (!mac && !ttLockId) return '-';
    if (mac && ttLockId) return `${mac}/${ttLockId}`;
    return mac || String(ttLockId);
  },

  formatValidity(start, end) {
    // 0 = 永久；若 start=0 且 end=0 视为永久
    if ((!start || start === 0) && (!end || end === 0)) {
      return '永久';
    }
    const startStr = this.formatTime(start);
    const endStr = this.formatTime(end);
    if (startStr && endStr) {
      return `${startStr} -- ${endStr}`;
    }
    if (startStr) return `${startStr} 起 · 永久`;
    if (endStr) return `至 ${endStr}`;
    return '永久';
  },

  formatTime(ts) {
    if (!ts || ts === 0) return '';
    const d = new Date(ts);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const h = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    return `${y}.${m}.${day} ${h}:${min}`;
  },

  /**
   * 刷新电量：从后端拉一次最新详情。
   * 电池最近一次被同步是在「同步操作记录」时由后端 RecordService
   * 从首条日志解析后写回 lock.electricQuantity 的，因此：
   *   - 若用户从未同步过操作记录，电量始终是 0
   *   - 想要"实时"读电量需走 ttlock.getLockVersion 连锁读取，留待后续扩展
   */
  async onRefreshBattery() {
    if (this.data.refreshingBattery) return;
    this.setData({ refreshingBattery: true });
    try {
      const data = await lockApi.getDetail(this.data.lockId);
      const eq = data.electricQuantity || 0;
      this.setData({ electricQuantity: eq });
      if (eq > 0) {
        wx.showToast({ title: `电量 ${eq}%`, icon: 'none' });
      } else {
        wx.showToast({ title: '暂无电量数据，请先同步记录', icon: 'none' });
      }
    } catch (err) {
      console.error('Refresh battery failed:', err);
    } finally {
      this.setData({ refreshingBattery: false });
    }
  },

  /**
   * 同步操作记录（上传数据）：
   * 1) 从后端获取最新 lockData
   * 2) 通过蓝牙插件读取锁端操作日志（logType=2 取全部，含 IC 卡/密码/指纹等）
   * 3) 调 recordApi.sync 把日志推给后端，由后端写入 Record 表 + 转推 TTLock 云端
   */
  async onUploadRecords() {
    if (this.data.uploading) return;
    if (!this.data.lockId) {
      wx.showToast({ title: '锁信息不完整', icon: 'none' });
      return;
    }

    this.setData({ uploading: true });
    let unlockData;
    try {
      wx.showLoading({ title: '获取开锁数据...' });
      unlockData = await lockApi.getUnlockData(this.data.lockId);
    } catch (err) {
      wx.hideLoading();
      this.setData({ uploading: false });
      console.error('Get unlock data failed:', err);
      wx.showToast({ title: '获取开锁数据失败', icon: 'none' });
      return;
    }

    const freshLockData = unlockData && unlockData.lockData;
    if (!freshLockData) {
      wx.hideLoading();
      this.setData({ uploading: false });
      wx.showToast({ title: '锁数据不可用，请靠近重试', icon: 'none' });
      return;
    }

    try {
      wx.showLoading({ title: '读取锁端记录...' });
      const logResult = await Promise.race([
        ttlock.getOperationLog(freshLockData, 2),
        new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 15000))
      ]);

      const log = logResult && logResult.log;
      if (!log) {
        wx.hideLoading();
        wx.showToast({ title: '未读取到记录', icon: 'none' });
        return;
      }

      // 插件返回的 log 实际是 JSON 字符串（官方 demo 也是 JSON.parse 后再用）。
      // 兼容一下：字符串直接用，对象/数组走 JSON.stringify。
      const logJson = typeof log === 'string' ? log : JSON.stringify(log);

      // 解析为数组用于显示条数 —— 不能用字符串 length，否则空数组 "[]" 会显示"已同步 2 条"
      let logList = [];
      if (Array.isArray(log)) {
        logList = log;
      } else if (typeof log === 'string') {
        try { logList = JSON.parse(log || '[]') || []; } catch (e) { logList = []; }
      }

      wx.showLoading({ title: '上传中...' });
      await recordApi.sync(this.data.lockId, logJson);

      wx.hideLoading();
      const count = Array.isArray(logList) ? logList.length : 0;
      const now = new Date();
      const timeStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      this.setData({ lastSyncText: `${timeStr} · ${count} 条` });
      wx.showToast({ title: `已同步 ${count} 条`, icon: 'success' });
    } catch (err) {
      wx.hideLoading();
      console.error('Upload records failed:', err);
      wx.showToast({ title: err && err.message ? err.message : '同步失败', icon: 'none' });
    } finally {
      this.setData({ uploading: false });
      ttlock.finishOperations().catch(() => {});
    }
  },

  onUnload() {
    ttlock.stopAllOperations().catch(() => {});
  },

  // ===== 修改锁名称 =====

  onCopyField(e) {
    const field = e.currentTarget.dataset.field;
    const labelMap = { lockAlias: '锁编号', macId: 'MAC/ID', lockName: '锁名称' };
    let text = '';
    if (field === 'lockAlias') {
      text = this.data.lockAlias || this.data.lockName || '';
    } else if (field === 'macId') {
      text = this.data.macIdText;
    } else if (field === 'lockName') {
      text = this.data.lockName;
    }
    if (!text || text === '-') {
      wx.showToast({ title: `${labelMap[field] || '内容'}暂不可用`, icon: 'none' });
      return;
    }
    wx.setClipboardData({
      data: text,
      success: () => {
        wx.showToast({ title: `已复制${labelMap[field] || ''}`, icon: 'success' });
      },
      fail: (err) => {
        console.error('Copy failed:', err);
        wx.showToast({ title: '复制失败，请重试', icon: 'none' });
      }
    });
  },

  onEditName() {
    if (this.data.keyType === 'common') {
      wx.showToast({ title: '当前角色无修改权限', icon: 'none' });
      return;
    }
    this.setData({
      showNameModal: true,
      editingName: this.data.lockName || ''
    });
  },

  onNameInput(e) {
    this.setData({ editingName: e.detail.value });
  },

  onCancelName() {
    if (this.data.savingName) return;
    this.setData({ showNameModal: false, editingName: '' });
  },

  noop() {},

  async onConfirmName() {
    if (this.data.savingName) return;
    const newName = (this.data.editingName || '').trim();
    if (!newName) {
      wx.showToast({ title: '请输入新名称', icon: 'none' });
      return;
    }
    if (newName === this.data.lockName) {
      this.setData({ showNameModal: false, editingName: '' });
      return;
    }

    this.setData({ savingName: true });
    try {
      await lockApi.updateName(this.data.lockId, newName);
      this.setData({
        lockName: newName,
        lockAlias: newName,
        showNameModal: false,
        editingName: ''
      });
      wx.setNavigationBarTitle({ title: `${newName} · 设置` });
      wx.showToast({ title: '已保存', icon: 'success' });
    } catch (err) {
      console.error('Update lock name failed:', err);
      // request.js 已统一弹错误信息
    } finally {
      this.setData({ savingName: false });
    }
  },

  // ===== 危险操作 =====

  getDeleteConfirmText() {
    return this.data.lockAlias || this.data.lockName || this.data.macIdText;
  },

  onDeleteLock() {
    if (this.data.keyType !== 'owner') {
      wx.showToast({ title: '仅锁拥有者可删除', icon: 'none' });
      return;
    }
    if (!this.data.lockId) {
      wx.showToast({ title: '锁信息不完整', icon: 'none' });
      return;
    }
    this.setData({
      showDeleteModal: true,
      deleteConfirmText: ''
    });
  },

  onDeleteConfirmInput(e) {
    this.setData({ deleteConfirmText: e.detail.value });
  },

  onCancelDelete() {
    if (this.data.deletingLock) return;
    this.setData({ showDeleteModal: false, deleteConfirmText: '' });
  },

  async onConfirmDelete() {
    if (this.data.deletingLock) return;
    const confirmText = this.getDeleteConfirmText();
    const inputText = (this.data.deleteConfirmText || '').trim();
    if (!confirmText || confirmText === '-') {
      wx.showToast({ title: '锁编号不可用，请刷新后重试', icon: 'none' });
      return;
    }
    if (inputText !== confirmText) {
      wx.showToast({ title: '请输入正确的锁编号', icon: 'none' });
      return;
    }

    wx.showModal({
      title: '确认删除',
      content: '删除后将从您的账户和云端移除该锁，此操作不可恢复。确定继续吗？',
      confirmText: '删除',
      confirmColor: '#ff4d4f',
      success: async (res) => {
        if (!res.confirm) return;

        this.setData({ deletingLock: true });
        try {
          // 1) 物理重置锁：必须用户站在锁边（蓝牙范围内）。这一步成功之前不能调 HTTP，
          // 否则会出现"服务端已删、锁端未重置"的不一致状态。
          if (!this.data.lockData) {
            throw new Error('锁数据不可用，请返回上一页重新进入');
          }
          wx.showLoading({ title: '请靠近锁并保持连接...', mask: true });
          const resetResult = await Promise.race([
            ttlock.resetLock(this.data.lockData),
            new Promise((_, reject) => setTimeout(() => reject(new Error('蓝牙重置超时，请靠近锁后重试')), 20000))
          ]);
          if (resetResult && resetResult.errorCode !== 0) {
            throw new Error((resetResult && (resetResult.errorMsg || resetResult.description)) || '锁重置失败');
          }

          // 2) 服务端 / TTLock 云端记录删除
          wx.showLoading({ title: '删除中...', mask: true });
          await lockApi.delete(this.data.lockId, confirmText);

          wx.hideLoading();
          wx.showToast({ title: '删除成功', icon: 'success' });
          this.setData({ showDeleteModal: false, deleteConfirmText: '' });
          setTimeout(() => {
            wx.reLaunch({ url: '/pages/lock-list/lock-list' });
          }, 1500);
        } catch (err) {
          wx.hideLoading();
          console.error('Delete lock failed:', err);
          // api.js 内部对业务错误已经弹过 toast，但若 handleResponse 内层 JS 抛错
          // （比如后端返非 JSON 响应导致 data.code 读不到属性），就没有任何 UI 提示。
          // 这里做兜底，确保用户至少能看到一行错误。
          const msg = (err && (err.message || err.errMsg)) || '删除失败，请稍后重试';
          wx.showToast({ title: msg, icon: 'none' });
        } finally {
          this.setData({ deletingLock: false });
          ttlock.finishOperations().catch(() => {});
        }
      }
    });
  },

  onQuitAdmin() {
    const { keyType, ekeyRecordId } = this.data;
    if (keyType !== 'admin') {
      wx.showToast({ title: '当前角色无法退出管理', icon: 'none' });
      return;
    }
    if (!ekeyRecordId) {
      wx.showToast({ title: '管理员钥匙信息不完整', icon: 'none' });
      return;
    }

    wx.showModal({
      title: '退出管理',
      content: '退出后您将无法继续管理或打开这把锁，确定继续吗？',
      confirmText: '退出',
      confirmColor: '#ff4d4f',
      success: async (res) => {
        if (!res.confirm) return;

        try {
          wx.showLoading({ title: '退出中...', mask: true });
          await keyApi.delete(ekeyRecordId);
          wx.hideLoading();
          wx.showToast({ title: '已退出管理', icon: 'success' });
          setTimeout(() => {
            wx.reLaunch({ url: '/pages/lock-list/lock-list' });
          }, 800);
        } catch (err) {
          wx.hideLoading();
          console.error('Quit admin failed:', err);
        }
      }
    });
  }
});
