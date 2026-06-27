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

  /**
   * 微信一键登录：getPhoneNumber 按钮回调。
   * 后端 /api/user/wxLogin 用 wx.login 的 code 换 openid，
   * 用 e.detail.code 换手机号；命中已有账号直接发 JWT，未命中静默建号。
   */
  async onWxLogin(e) {
    const detail = e && e.detail;
    if (!detail || !detail.code) {
      // 用户拒绝授权或被微信侧拦截，errMsg 形如 "getPhoneNumber:fail user deny"
      const msg = (detail && detail.errMsg) || '';
      if (/deny|cancel/i.test(msg)) {
        return;
      }
      wx.showToast({ title: '微信授权失败', icon: 'none' });
      return;
    }
    const phoneCode = detail.code;

    try {
      wx.showLoading({ title: '登录中...', mask: true });

      const loginRes = await new Promise((resolve, reject) => {
        wx.login({ success: resolve, fail: reject });
      });
      if (!loginRes || !loginRes.code) {
        throw new Error('wx.login 失败');
      }

      const result = await userApi.wxLogin({ code: loginRes.code, phoneCode });
      app.setLoginInfo(result.token, result.userInfo);

      wx.hideLoading();
      wx.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/lock-list/lock-list' });
      }, 800);
    } catch (err) {
      wx.hideLoading();
      console.error('wxLogin failed:', err);
      // request.js 已在业务码非 0 时统一弹 toast，这里仅 fallback
      if (err && !err.code) {
        wx.showToast({ title: err.message || '登录失败', icon: 'none' });
      }
    }
  },

  goRegister() {
    wx.navigateTo({ url: '/pages/register/register' });
  }
});
