const app = getApp();
const { lockApi, recordApi } = require('../../utils/api');
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
    savingName: false
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
        ekeyStartDate: startDate,
        ekeyEndDate: endDate,
        groupId: data.groupId || 0,
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

      // 插件返回的 log 可能是数组或对象，统一序列化为 JSON 字符串
      const logJson = typeof log === 'string' ? log : JSON.stringify(log);

      wx.showLoading({ title: '上传中...' });
      await recordApi.sync(this.data.lockId, logJson);

      wx.hideLoading();
      const count = Array.isArray(log) ? log.length : (log && log.length) || 0;
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
  }
});
