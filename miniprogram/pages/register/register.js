const app = getApp();
const { userApi } = require('../../utils/api');

Page({
  data: {
    username: '',
    code: '',
    password: '',
    confirmPassword: '',
    codeBtnText: '获取验证码',
    codeBtnDisabled: false
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value });
  },

  onCodeInput(e) {
    this.setData({ code: e.detail.value });
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  onConfirmPasswordInput(e) {
    this.setData({ confirmPassword: e.detail.value });
  },

  async onSendCode() {
    const { username } = this.data;
    
    if (!username) {
      wx.showToast({ title: '请输入手机号/邮箱', icon: 'none' });
      return;
    }

    try {
      await userApi.sendCode(username);
      
      wx.showToast({ title: '验证码已发送', icon: 'success' });
      
      // Start countdown
      this.setData({ codeBtnDisabled: true });
      let seconds = 60;
      this.setData({ codeBtnText: `${seconds}s` });
      
      this.timer = setInterval(() => {
        seconds--;
        if (seconds <= 0) {
          clearInterval(this.timer);
          this.setData({
            codeBtnText: '获取验证码',
            codeBtnDisabled: false
          });
        } else {
          this.setData({ codeBtnText: `${seconds}s` });
        }
      }, 1000);
    } catch (err) {
      console.error('Send code failed:', err);
    }
  },

  async onRegister() {
    const { username, code, password, confirmPassword } = this.data;
    
    if (!username) {
      wx.showToast({ title: '请输入手机号/邮箱', icon: 'none' });
      return;
    }
    
    if (!code) {
      wx.showToast({ title: '请输入验证码', icon: 'none' });
      return;
    }
    
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }
    
    if (password.length < 6 || password.length > 20) {
      wx.showToast({ title: '密码长度需为6-20位', icon: 'none' });
      return;
    }
    
    if (password !== confirmPassword) {
      wx.showToast({ title: '两次密码不一致', icon: 'none' });
      return;
    }

    try {
      wx.showLoading({ title: '注册中...' });
      
      const result = await userApi.register({ username, code, password });
      
      app.setLoginInfo(result.token, result.userInfo);
      
      wx.hideLoading();
      wx.showToast({ title: '注册成功', icon: 'success' });
      
      setTimeout(() => {
        wx.switchTab({ url: '/pages/lock-list/lock-list' });
      }, 1000);
    } catch (err) {
      wx.hideLoading();
      console.error('Register failed:', err);
    }
  },

  goLogin() {
    wx.navigateBack();
  },

  onUnload() {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }
});
