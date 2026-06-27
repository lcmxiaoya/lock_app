const { keyApi } = require('../../utils/api');

Page({
  data: {
    lockId: null,
    lockName: '',
    receiverUsername: '',
    keyName: '',
    selectedType: 'timed',     // 'timed' | 'permanent'
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
    const end = new Date(now.getTime() + 60 * 60 * 1000);
    this.setData({
      startDate: this.formatDate(now),
      endDate: this.formatDate(end),
      startTime: this.formatTime(now),
      endTime: this.formatTime(end)
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

  onSelectType(e) {
    this.setData({ selectedType: e.currentTarget.dataset.type });
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

  async onSend() {
    const { receiverUsername, lockId, keyName, selectedType,
            startDate, endDate, startTime, endTime } = this.data;

    if (!receiverUsername) {
      wx.showToast({ title: '请输入手机号或邮箱', icon: 'none' });
      return;
    }
    if (!lockId) {
      wx.showToast({ title: '锁信息不可用', icon: 'none' });
      return;
    }

    let startTimestamp = 0;
    let endTimestamp = 0;
    if (selectedType === 'timed') {
      const startStr = `${startDate.replace(/-/g, '/')} ${startTime || '00:00'}`;
      const endStr = `${endDate.replace(/-/g, '/')} ${endTime || '00:00'}`;
      startTimestamp = new Date(startStr).getTime();
      endTimestamp = new Date(endStr).getTime();
      if (isNaN(startTimestamp) || isNaN(endTimestamp)) {
        wx.showToast({ title: '日期格式无效', icon: 'none' });
        return;
      }
      if (endTimestamp <= startTimestamp) {
        wx.showToast({ title: '失效时间必须晚于生效时间', icon: 'none' });
        return;
      }
    }

    try {
      wx.showLoading({ title: '授权中...', mask: true });
      await keyApi.sendAdmin({
        receiverUsername,
        lockId,
        keyName: keyName || '管理员',
        startDate: startTimestamp,
        endDate: endTimestamp,
        remarks: ''
      });
      wx.hideLoading();
      wx.showToast({ title: '授权成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 800);
    } catch (err) {
      wx.hideLoading();
      console.error('Send admin failed:', err);
      // request.js 已统一弹错误信息
    }
  }
});
