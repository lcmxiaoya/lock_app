const app = getApp();
const { userApi } = require('../../utils/api');

Page({
  data: {
    username: '',
    password: ''
  },

  onShow() {
    if (app.checkLogin()) {
      wx.switchTab({ url: '/pages/lock-list/lock-list' });
    }
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value });
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  async onLogin() {
    const { username, password } = this.data;
    
    if (!username) {
      wx.showToast({ title: '请输入手机号/邮箱', icon: 'none' });
      return;
    }
    
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }

    try {
      wx.showLoading({ title: '登录中...' });
      
      const result = await userApi.login({ username, password });
      
      app.setLoginInfo(result.token, result.userInfo);
      
      wx.hideLoading();
      wx.showToast({ title: '登录成功', icon: 'success' });
      
      setTimeout(() => {
        wx.switchTab({ url: '/pages/lock-list/lock-list' });
      }, 1000);
    } catch (err) {
      wx.hideLoading();
      console.error('Login failed:', err);
    }
  },

  goRegister() {
    wx.navigateTo({ url: '/pages/register/register' });
  }
});
