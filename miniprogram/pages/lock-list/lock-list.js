const app = getApp();
const { lockApi } = require('../../utils/api');

Page({
  data: {
    // 合规要求:微信审核明确要求"先浏览后授权"
    // 不在 onLoad 阶段弹任何授权 / 跳转登录页,默认让用户停留在首页
    isLoggedIn: false,
    locks: [],
    pageNo: 1,
    pageSize: 20,
    total: 0,
    loading: false
  },

  onLoad() {
    this.syncLoginState();
  },

  onShow() {
    this.syncLoginState();
    if (this.data.isLoggedIn) {
      this.loadLocks();
    }
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 });
    }
  },

  // 同步登录态:不触发任何跳转 / 授权
  syncLoginState() {
    this.setData({ isLoggedIn: app.checkLogin() });
  },

  resetAndLoad() {
    this.setData({ pageNo: 1, locks: [] });
    this.loadLocks();
  },

  async loadLocks() {
    // 未登录不强制跳转,仅直接返回,让空状态展示"请登录"引导
    if (!app.checkLogin()) {
      this.setData({ isLoggedIn: false });
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
    if (!this.data.isLoggedIn) return;
    const { pageNo, pageSize, total } = this.data;
    if (this.data.locks.length < total) {
      this.setData({ pageNo: pageNo + 1 });
      this.loadLocks();
    }
  },

  onPullDownRefresh() {
    if (this.data.isLoggedIn) {
      this.resetAndLoad();
    }
    wx.stopPullDownRefresh();
  },

  goLockControl(e) {
    if (!this.data.isLoggedIn) {
      this.goLogin();
      return;
    }
    const lockId = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/lock-control/lock-control?lockId=${lockId}`
    });
  },

  goAddLock() {
    if (!this.data.isLoggedIn) {
      this.goLogin();
      return;
    }
    wx.navigateTo({
      url: '/pages/lock-control/lock-control?mode=add'
    });
  },

  // 用户主动点击"登录"按钮时才跳转
  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' });
  }
});
