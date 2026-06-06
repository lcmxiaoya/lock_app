const app = getApp();
const { keyApi } = require('../../utils/api');

Page({
  data: {
    receiverUsername: '',
    lockId: null,
    lockName: '',
    keyName: '',
    selectedType: 'timed',
    startDate: '',
    endDate: '',
    startTime: '',
    endTime: ''
  },

  onLoad(options) {
    if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
    }
    if (options.lockName) {
      this.setData({ lockName: decodeURIComponent(options.lockName) });
    }
    this.setDefaultDates();
  },

  setDefaultDates() {
    const now = new Date();
    const end = new Date(now.getTime() + 24 * 60 * 60 * 1000);
    this.setData({
      startDate: this.formatDate(now),
      endDate: this.formatDate(end),
      startTime: this.formatTime(now),
      endTime: this.formatTime(now)
    });
  },

  formatDate(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  },

  formatTime(d) {
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    return `${h}:${m}`;
  },

  onReceiverInput(e) {
    this.setData({ receiverUsername: e.detail.value });
  },

  onClearReceiver() {
    this.setData({ receiverUsername: '' });
  },

  onKeyNameInput(e) {
    this.setData({ keyName: e.detail.value });
  },

  onSelectType(e) {
    const type = e.currentTarget.dataset.type;
    this.setData({ selectedType: type });
    this.setDefaultDates();
  },

  onStartDateChange(e) {
    this.setData({ startDate: e.detail.value });
  },

  onEndDateChange(e) {
    this.setData({ endDate: e.detail.value });
  },

  onStartTimeChange(e) {
    this.setData({ startTime: e.detail.value });
  },

  onEndTimeChange(e) {
    this.setData({ endTime: e.detail.value });
  },

  async onSendKey() {
    const { receiverUsername, lockId, keyName, selectedType, startDate, endDate } = this.data;

    if (!receiverUsername) {
      wx.showToast({ title: '请输入接收者手机号/邮箱', icon: 'none' });
      return;
    }

    if (!lockId) {
      wx.showToast({ title: '锁信息不可用', icon: 'none' });
      return;
    }

    let startTimestamp = 0;
    let endTimestamp = 0;

    if (selectedType === 'timed' || selectedType === 'cyclic') {
      if (!startDate || !endDate) {
        wx.showToast({ title: '请选择有效期', icon: 'none' });
        return;
      }
      const startDateTime = `${startDate.replace(/-/g, '/')} ${this.data.startTime || '00:00'}`;
      const endDateTime = `${endDate.replace(/-/g, '/')} ${this.data.endTime || '00:00'}`;
      startTimestamp = new Date(startDateTime).getTime();
      endTimestamp = new Date(endDateTime).getTime();
      if (isNaN(startTimestamp) || isNaN(endTimestamp)) {
        wx.showToast({ title: '日期格式无效', icon: 'none' });
        return;
      }
    } else if (selectedType === 'single') {
      startTimestamp = Date.now();
      endTimestamp = startTimestamp + 6 * 60 * 60 * 1000;
    } else if (selectedType === 'permanent') {
      startTimestamp = 0;
      endTimestamp = 0;
    }

    try {
      wx.showLoading({ title: '发送中...' });

      await keyApi.send({
        receiverUsername,
        lockId,
        keyName: keyName || '分享钥匙',
        startDate: startTimestamp,
        endDate: endTimestamp,
        remarks: ''
      });

      wx.hideLoading();
      wx.showToast({ title: '发送成功', icon: 'success' });

      setTimeout(() => {
        wx.navigateBack();
      }, 1000);
    } catch (err) {
      wx.hideLoading();
      console.error('Send key failed:', err);
      wx.showToast({ title: err.errMsg || err.message || '发送失败', icon: 'none' });
    }
  }
});
