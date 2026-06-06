const app = getApp();
const { pwdApi, lockApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

Page({
  data: {
    lockId: null,
    keyboardPwd: '',
    pwdName: '',
    pwdType: 2,
    startDateStr: '',
    endDateStr: '',
    startDate: 0,
    endDate: 0,
    adding: false
  },

  onLoad(options) {
    if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
    }
  },

  onPwdInput(e) {
    const val = e.detail.value.replace(/\D/g, '');
    this.setData({ keyboardPwd: val });
  },

  onPwdNameInput(e) {
    this.setData({ pwdName: e.detail.value });
  },

  onStartDateChange(e) {
    this.setData({
      startDateStr: e.detail.value,
      startDate: new Date(e.detail.value).getTime()
    });
  },

  onEndDateChange(e) {
    this.setData({
      endDateStr: e.detail.value,
      endDate: new Date(e.detail.value + ' 23:59:59').getTime()
    });
  },

  async onAddCustom() {
    const { lockId, keyboardPwd, pwdName, startDate, endDate, startDateStr, endDateStr } = this.data;

    if (this.data.adding) return;

    if (!keyboardPwd || keyboardPwd.length < 4) {
      wx.showToast({ title: '密码需为4-9位数字', icon: 'none' });
      return;
    }
    if (keyboardPwd.length > 9) {
      wx.showToast({ title: '密码长度不能超过9位', icon: 'none' });
      return;
    }
    if (!startDateStr || !endDateStr) {
      wx.showToast({ title: '请选择有效期', icon: 'none' });
      return;
    }
    if (endDate <= startDate) {
      wx.showToast({ title: '结束时间必须晚于开始时间', icon: 'none' });
      return;
    }

    this.setData({ adding: true });

    try {
      wx.showLoading({ title: '获取锁数据...' });
      const lockDetail = await lockApi.getDetail(lockId);
      const lockData = lockDetail.lockData;

      wx.showLoading({ title: '蓝牙写入密码...' });
      const startStr = this.formatPickerDate(startDate);
      const endStr = this.formatPickerDate(endDate);
      await ttlock.createCustomPasscode(lockData, keyboardPwd, startStr, endStr);

      wx.showLoading({ title: '同步到云端...' });
      await pwdApi.addCustom({
        lockId,
        keyboardPwd,
        pwdName: pwdName || undefined,
        pwdType: 2,
        startDate,
        endDate
      });

      wx.hideLoading();
      wx.showToast({ title: '添加成功', icon: 'success' });

      setTimeout(() => {
        wx.navigateBack();
      }, 1000);
    } catch (err) {
      wx.hideLoading();
      this.setData({ adding: false });
      console.error('Add custom password failed:', err);
      wx.showToast({ title: '添加失败: ' + (err.errMsg || err.message || '未知错误'), icon: 'none' });
    } finally {
      ttlock.finishOperations().catch(() => {});
    }
  },

  formatPickerDate(ts) {
    const d = new Date(ts);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const h = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    const s = String(d.getSeconds()).padStart(2, '0');
    return `${y}-${m}-${day} ${h}:${min}:${s}`;
  }
});
