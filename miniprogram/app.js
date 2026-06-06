App({
  globalData: {
    userInfo: null,
    token: null,
    baseUrl: 'http://172.28.44.133:8080'
  },

  onLaunch() {
    // Check if user is logged in
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
