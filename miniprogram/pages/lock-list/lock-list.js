const app = getApp();
const { lockApi } = require('../../utils/api');

Page({
  data: {
    locks: [],
    pageNo: 1,
    pageSize: 20,
    total: 0,
    loading: false
  },

  onLoad() {
    this.loadLocks();
  },

  onShow() {
    this.loadLocks();
  },

  resetAndLoad() {
    this.setData({ pageNo: 1, locks: [] });
    this.loadLocks();
  },

  async loadLocks() {
    if (!app.checkLogin()) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    if (this.data.loading) return;

    this.setData({ loading: true });
    try {
      wx.showLoading({ title: '加载中...' });
      const result = await lockApi.getList(this.data.pageNo, this.data.pageSize);
      this.setData({
        locks: this.data.pageNo === 1 ? result.list : [...this.data.locks, ...result.list],
        total: result.total,
        loading: false
      });
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      this.setData({ loading: false });
      console.error('Load locks failed:', err);
    }
  },

  onReachBottom() {
    const { pageNo, pageSize, total } = this.data;
    if (this.data.locks.length < total) {
      this.setData({ pageNo: pageNo + 1 });
      this.loadLocks();
    }
  },

  onPullDownRefresh() {
    this.resetAndLoad();
    wx.stopPullDownRefresh();
  },

  goLockControl(e) {
    const lockId = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/lock-control/lock-control?lockId=${lockId}`
    });
  },

  goAddLock() {
    wx.navigateTo({
      url: '/pages/lock-control/lock-control?mode=add'
    });
  }
});
