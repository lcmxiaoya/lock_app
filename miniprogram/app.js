App({
  globalData: {
    userInfo: null,
    token: null,
    baseUrl: 'http://10.111.193.133:8080'
  },

  // true = 微信云托管线上，false = 本地开发
  useCloud: true,
  cloudHosting: {
    env: 'prod-d7gkt6iuhf270390a',
    service: 'ttlock-server'
  },

  onLaunch() {
    // 本地调试：打开手机端 vConsole 面板，能直接看到 console.log / console.error / 报错 errMsg
    // 仅 useCloud === false 时启用；切到云托管线上版前不会带这玩意儿
    if (!this.useCloud && wx.setEnableDebug) {
      wx.setEnableDebug({ enableDebug: true });
    }

    if (this.useCloud) {
      wx.cloud.init();
    }

    const token = wx.getStorageSync('token');
    const userInfo = wx.getStorageSync('userInfo');

    if (token) {
      this.globalData.token = token;
      this.globalData.userInfo = userInfo;
    }
  },

  // Check login status
  checkLogin() {
    return !!this.globalData.token;
  },

  // Set login info
  setLoginInfo(token, userInfo) {
    this.globalData.token = token;
    this.globalData.userInfo = userInfo;
    wx.setStorageSync('token', token);
    wx.setStorageSync('userInfo', userInfo);
  },

  // Clear login info
  clearLoginInfo() {
    this.globalData.token = null;
    this.globalData.userInfo = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
  }
});
