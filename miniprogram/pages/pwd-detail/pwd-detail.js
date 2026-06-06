const { pwdApi } = require('../../utils/api');

Page({
  data: {
    pwd: {},
    lockId: null,
    validPeriod: '',
    sendTime: '',
    statusBarHeight: 0,
    navBarHeight: 0
  },

  onLoad(options) {
    const systemInfo = wx.getSystemInfoSync();
    const menuButton = wx.getMenuButtonBoundingClientRect();
    const statusBarHeight = systemInfo.statusBarHeight;
    const navBarHeight = (menuButton.top - statusBarHeight) * 2 + menuButton.height;
    this.setData({ statusBarHeight, navBarHeight });

    if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
    }

    if (options.pwdData) {
      try {
        const pwd = JSON.parse(decodeURIComponent(options.pwdData));
        if (pwd.keyboardPwdName) {
          try { pwd.keyboardPwdName = decodeURIComponent(pwd.keyboardPwdName); } catch (e) {}
        }
        this.setData({ pwd });
        this.formatDetail(pwd);
      } catch (e) {
        console.error('Parse pwd data failed:', e);
        wx.showToast({ title: '数据加载失败', icon: 'none' });
      }
    }
  },

  formatDetail(pwd) {
    const validPeriod = this.formatPeriod(pwd.startDate, pwd.endDate, pwd.pwdType);
    const sendTime = this.formatDate(pwd.createdAt);
    this.setData({ validPeriod, sendTime });
  },

  formatPeriod(startDate, endDate, pwdType) {
    if (pwdType === 2 || (!startDate && !endDate)) {
      return '永久有效';
    }
    const start = this.formatDate(startDate);
    const end = this.formatDate(endDate);
    if (start && end) {
      return `${start}\n${end}`;
    }
    if (start) return start;
    return '';
  },

  formatDate(ts) {
    if (!ts || ts === 0) return '';
    const date = new Date(ts);
    if (isNaN(date.getTime())) return '';
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const h = String(date.getHours()).padStart(2, '0');
    const min = String(date.getMinutes()).padStart(2, '0');
    return `${y}.${m}.${d} ${h}:${min}`;
  },

  onGoBack() {
    wx.navigateBack();
  },

  onCopyPwd() {
    const pwd = this.data.pwd.keyboardPwd;
    if (!pwd) return;
    wx.setClipboardData({
      data: pwd,
      success() {
        wx.showToast({ title: '已复制密码', icon: 'success' });
      }
    });
  },

  onEditName() {
    const { pwd, lockId } = this.data;
    wx.showModal({
      title: '修改姓名',
      editable: true,
      placeholderText: '请输入姓名',
      content: pwd.keyboardPwdName || '',
      success: async (res) => {
        if (res.confirm && res.content) {
          try {
            wx.showLoading({ title: '修改中...' });
            await pwdApi.change({
              lockId,
              pwdId: pwd.pwdId,
              keyboardPwdName: res.content
            });
            this.setData({ 'pwd.keyboardPwdName': res.content });
            wx.hideLoading();
            wx.showToast({ title: '修改成功', icon: 'success' });
          } catch (err) {
            wx.hideLoading();
            console.error('Change name failed:', err);
            wx.showToast({ title: '修改失败', icon: 'none' });
          }
        }
      }
    });
  },

  onViewRecords() {
    const { lockId, pwd } = this.data;
    if (!lockId) return;
    const keyboardPwd = pwd.keyboardPwd ? encodeURIComponent(pwd.keyboardPwd) : '';
    wx.navigateTo({
      url: `/pages/record-list/record-list?lockId=${lockId}&keyboardPwd=${keyboardPwd}`
    });
  },

  onDelete() {
    const { pwd, lockId } = this.data;
    wx.showModal({
      title: '确认删除',
      content: `确定要删除密码 ${pwd.keyboardPwd || ''} 吗？`,
      success: async (res) => {
        if (res.confirm) {
          try {
            wx.showLoading({ title: '删除中...' });
            await pwdApi.delete(lockId, pwd.pwdId);
            wx.hideLoading();
            wx.showToast({ title: '删除成功', icon: 'success' });
            setTimeout(() => {
              wx.navigateBack();
            }, 1000);
          } catch (err) {
            wx.hideLoading();
            console.error('Delete password failed:', err);
            wx.showToast({ title: '删除失败', icon: 'none' });
          }
        }
      }
    });
  }
});
