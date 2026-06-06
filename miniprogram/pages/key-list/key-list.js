const app = getApp();
const { keyApi, recordApi } = require('../../utils/api');

Page({
  data: {
    keys: [],
    lockId: null,
    lockName: '',
    pageNo: 1,
    pageSize: 20,
    total: 0,
    loading: false,
    detailKey: null,
    showDetail: false,
    keyRecords: [],
    recordsLoading: false
  },

  formatTime(ts) {
    if (!ts || ts === 0) return '';
    const d = new Date(ts);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const h = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    const sec = String(d.getSeconds()).padStart(2, '0');
    return `${y}-${m}-${day} ${h}:${min}:${sec}`;
  },

  formatDate(d) {
    if (!d) return '';
    if (typeof d === 'string') return d.substring(0, 19).replace('T', ' ');
    return '';
  },

  onLoad(options) {
    if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
    }
    if (options.lockName) {
      this.setData({ lockName: decodeURIComponent(options.lockName) });
    }
  },

  onShow() {
    this.resetAndLoad();
  },

  resetAndLoad() {
    this.setData({ pageNo: 1, keys: [] });
    this.loadKeys();
  },

  async loadKeys() {
    const { lockId, pageNo, pageSize } = this.data;
    if (!lockId || this.data.loading) return;

    this.setData({ loading: true });
    try {
      wx.showLoading({ title: '加载中...' });
      const result = await keyApi.getList(lockId, pageNo, pageSize);
      const list = result.list || [];
      const now = Date.now();
      list.forEach(k => {
        k.startDateStr = this.formatTime(k.startDate);
        k.endDateStr = this.formatTime(k.endDate);
        k.createdAtStr = this.formatDate(k.createdAt);
        if (k.status === 'invalid') {
          k.validStatus = 'expired';
        } else if (k.endDate && k.endDate !== 0 && k.endDate < now) {
          k.validStatus = 'expired';
        } else {
          k.validStatus = 'valid';
        }
      });
      this.setData({
        keys: pageNo === 1 ? list : [...this.data.keys, ...list],
        total: result.total,
        loading: false
      });
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      this.setData({ loading: false });
      console.error('Load keys failed:', err);
    }
  },

  onReachBottom() {
    const { pageNo, pageSize, total } = this.data;
    if (this.data.keys.length < total) {
      this.setData({ pageNo: pageNo + 1 });
      this.loadKeys();
    }
  },

  goSendKey() {
    const { lockId, lockName } = this.data;
    wx.navigateTo({
      url: `/pages/key-send/key-send?lockId=${lockId}&lockName=${encodeURIComponent(lockName || '')}`
    });
  },

  async onKeyClick(e) {
    const index = e.currentTarget.dataset.index;
    const key = this.data.keys[index];
    this.setData({ detailKey: key, showDetail: true, keyRecords: [], recordsLoading: true });

    // Fetch operation records for this key
    const lockId = this.data.lockId;
    if (lockId && key.userId) {
      try {
        const recordResult = await recordApi.getListByKey(lockId, key.userId, 1, 5);
        const records = (recordResult.list || []).map(r => ({
          ...r,
          recordTimeStr: this.formatTime(r.recordTime)
        }));
        this.setData({ keyRecords: records, recordsLoading: false });
      } catch (err) {
        console.error('Load key records failed:', err);
        this.setData({ recordsLoading: false });
      }
    } else {
      this.setData({ recordsLoading: false });
    }
  },

  onCloseDetail() {
    this.setData({ showDetail: false, detailKey: null, keyRecords: [] });
  },

  onViewAllRecords() {
    const { lockId, detailKey } = this.data;
    if (!lockId || !detailKey) return;
    this.setData({ showDetail: false });
    wx.navigateTo({
      url: `/pages/record-list/record-list?lockId=${lockId}&keyId=${detailKey.keyId}`
    });
  },

  noop() {},

  onDeleteKey() {
    const key = this.data.detailKey;
    if (!key) return;
    const keyName = key.keyName || '这把钥匙';

    wx.showModal({
      title: '删除钥匙',
      content: `确定删除「${keyName}」吗？删除后无法恢复。`,
      success: async (res) => {
        if (res.confirm) {
          try {
            wx.showLoading({ title: '删除中...' });
            await keyApi.delete(key.id);
            wx.hideLoading();
            wx.showToast({ title: '删除成功', icon: 'success' });
            this.onCloseDetail();
            this.resetAndLoad();
          } catch (err) {
            wx.hideLoading();
            console.error('Delete key failed:', err);
          }
        }
      }
    });
  }
});
