const app = getApp();
const { userApi } = require('../../utils/api');

// 隐私政策 / 用户协议 —— 微信平台后台需另行配置对应的 HTTPS URL
const PRIVACY_POLICY = '星防卫小程序（以下简称"我们"）尊重并保护用户隐私。\n\n1. 收集信息：为提供开锁服务，我们会在您授权后获取微信绑定的手机号，并记录您添加的智能锁设备信息。\n2. 使用范围：上述信息仅用于账号登录、设备绑定、开锁指令下发与售后服务，不会用于其他用途。\n3. 存储与保护：信息加密存储于服务器，仅授权人员可访问，保存期限至您注销账号为止。\n4. 您的权利：您可随时查看、修改个人信息或注销账号。\n5. 联系方式：如有疑问，请通过小程序内反馈入口联系我们。\n\n更新日期：2026-07-10';

const USER_AGREEMENT = '欢迎使用星防卫小程序（以下简称"本服务"）。请仔细阅读本协议，使用即视为您同意以下条款。\n\n1. 服务内容：本服务通过微信授权为您提供智能锁的开锁、设备管理、钥匙分享等功能。\n2. 用户义务：您应妥善保管账号及手机设备，因泄露导致的损失由您自行承担。\n3. 行为规范：不得利用本服务从事任何违法违规活动，不得破解、攻击、干扰智能锁正常运行。\n4. 服务变更：我们可根据运营需要调整或中止部分功能，届时会通过小程序公告。\n5. 免责声明：因网络、硬件、第三方原因导致服务中断的，我们将在合理范围内尽快恢复。\n6. 协议变更：本协议可能根据业务调整进行更新，更新后继续使用即视为接受。\n\n更新日期：2026-07-10';

Page({
  data: {
    username: '',
    password: '',
    showAccountLogin: false,
    // 合规要求:用户须主动勾选才能登录,不得默认同意隐私政策 / 用户协议
    agreed: false
  },

  toggleAccountLogin() {
    this.setData({ showAccountLogin: !this.data.showAccountLogin });
  },

  toggleAgreement() {
    this.setData({ agreed: !this.data.agreed });
  },

  ensureAgreement() {
    if (this.data.agreed) {
      return true;
    }
    // 兜底拦截:open-type="getPhoneNumber" 按钮的微信系统授权弹窗无法用 JS 阻止,
    // 即便走到了 onWxLogin,也必须在此拒绝使用返回的 code,以保证"未明确同意我方协议前,不做任何业务处理"
    wx.showModal({
      title: '请先阅读并同意协议',
      content: '请先勾选下方的《隐私政策》和《用户协议》后再登录。',
      showCancel: false,
      confirmText: '我知道了'
    });
    return false;
  },

  // "手机号快捷登录"按钮的 bindtap 守门:未勾选协议时先强提示用户去勾选,
  // 再走微信 getPhoneNumber 流程,避免用户在被引导授权手机号之前未明确知悉我方协议
  onPhoneLoginTap() {
    if (this.data.agreed) {
      return;
    }
    wx.showModal({
      title: '请先阅读并同意协议',
      content: '使用手机号快捷登录前,请先勾选下方的《隐私政策》和《用户协议》。',
      showCancel: false,
      confirmText: '我知道了'
    });
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
    if (!this.ensureAgreement()) {
      return;
    }

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
    if (!this.ensureAgreement()) {
      return;
    }

    const detail = e && e.detail;
    if (!detail || !detail.code) {
      // 用户拒绝授权或被微信侧拦截，errMsg 形如 "getPhoneNumber:fail user deny"
      const msg = (detail && detail.errMsg) || '';
      if (/deny|cancel/i.test(msg)) {
        return;
      }
      wx.showToast({ title: '本地调试请使用账号登录', icon: 'none' });
      this.setData({ showAccountLogin: true });
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

  showPrivacyPolicy() {
    wx.showModal({
      title: '隐私政策',
      content: PRIVACY_POLICY,
      showCancel: false,
      confirmText: '我已阅读'
    });
  },

  showUserAgreement() {
    wx.showModal({
      title: '用户协议',
      content: USER_AGREEMENT,
      showCancel: false,
      confirmText: '我已阅读'
    });
  },

  goRegister() {
    wx.navigateTo({ url: '/pages/register/register' });
  }
});
