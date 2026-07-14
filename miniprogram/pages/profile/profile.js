const app = getApp();

// 问题反馈邮箱
const SUPPORT_EMAIL = '717255547@qq.com';

Page({
  data: {
    isLoggedIn: false,
    userInfo: null
  },

  onShow() {
    this.setData({
      isLoggedIn: app.checkLogin(),
      userInfo: app.globalData.userInfo
    });
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 });
    }
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      success: (res) => {
        if (res.confirm) {
          app.clearLoginInfo();
          this.setData({ isLoggedIn: false, userInfo: null });
        }
      }
    });
  },

  // 用户主动点击"登录"按钮才跳转
  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' });
  },

  // 点击"官方合作"菜单:复制邮箱到剪贴板并提示
  copySupportEmail() {
    wx.setClipboardData({
      data: SUPPORT_EMAIL,
      success: () => {
        wx.showToast({ title: '邮箱已复制', icon: 'success' });
      },
      fail: () => {
        wx.showToast({ title: '复制失败,请手动复制', icon: 'none' });
      }
    });
  }
});
