const { lockApi, icCardApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

Page({
  data: {
    lockId: null,
    lockName: '',
    cardName: '',
    selectedType: 'permanent',
    startDate: '',
    endDate: '',
    startTime: '',
    endTime: '',
    // 来自 lock-control / iccard-list：admin 自己的 ekey 有效期
    // 0 = 永久；非 0 时 picker 不能越过这个区间
    ekeyStartDate: 0,
    ekeyEndDate: 0,
    // 给 picker / 文案使用的派生值
    ekeyEndDateText: '',
    permanentDisabled: false,
    validityHint: '',
    lockData: '',
    adding: false,
    stepText: ''
  },

  onLoad(options) {
    if (options.lockId) this.setData({ lockId: parseInt(options.lockId) });
    if (options.lockName) this.setData({ lockName: decodeURIComponent(options.lockName) });
    const ekeyEnd = parseInt(options.ekeyEndDate || 0);
    this.setData({
      ekeyStartDate: parseInt(options.ekeyStartDate || 0),
      ekeyEndDate: ekeyEnd,
      permanentDisabled: ekeyEnd > 0,
      ekeyEndDateText: ekeyEnd > 0 ? this.fmtFullDateTime(ekeyEnd) : '',
      validityHint: ekeyEnd > 0
        ? `您是该锁的管理员，失效时间不能晚于 ${this.fmtFullDateTime(ekeyEnd)}`
        : ''
    });
    this.setDefaultDates();
    this.fetchLockData();
  },

  setDefaultDates() {
    const now = new Date();
    const end = new Date(now.getTime() + 60 * 60 * 1000);
    this.setData({
      startDate: this.fmtDate(now),
      endDate: this.fmtDate(end),
      startTime: this.fmtTime(now),
      endTime: this.fmtTime(end)
    });
  },

  fmtDate(d) {
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  },
  fmtTime(d) {
    return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  },
  fmtFullDateTime(ts) {
    if (!ts || ts === 0) return '';
    const d = new Date(ts);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  },

  onSelectType(e) {
    const t = e.currentTarget.dataset.type;
    if (t === 'permanent' && this.data.permanentDisabled) {
      wx.showToast({ title: '您自身有截止时间，无法设置永久', icon: 'none' });
      return;
    }
    this.setData({ selectedType: t });
  },
  onCardNameInput(e) { this.setData({ cardName: e.detail.value }); },
  onStartDateChange(e) { this.setData({ startDate: e.detail.value }); },
  onEndDateChange(e) { this.setData({ endDate: e.detail.value }); },
  onStartTimeChange(e) { this.setData({ startTime: e.detail.value }); },
  onEndTimeChange(e) { this.setData({ endTime: e.detail.value }); },

  async fetchLockData() {
    try {
      const detail = await lockApi.getDetail(this.data.lockId);
      this.setData({ lockData: detail.lockData || '' });
    } catch (err) {
      console.error('fetchLockData failed', err);
    }
  },

  buildTimestamps() {
    if (this.data.selectedType === 'permanent') {
      if (this.data.permanentDisabled) {
        wx.showToast({ title: '您自身有截止时间，无法设置永久', icon: 'none' });
        return null;
      }
      return { startDate: 0, endDate: 0 };
    }
    const start = new Date(`${this.data.startDate.replace(/-/g,'/')} ${this.data.startTime || '00:00'}`).getTime();
    const end   = new Date(`${this.data.endDate.replace(/-/g,'/')} ${this.data.endTime || '00:00'}`).getTime();
    if (isNaN(start) || isNaN(end) || end <= start) {
      wx.showToast({ title: '请检查有效期', icon: 'none' });
      return null;
    }
    // admin 自身的 ekey 区间硬约束
    const { ekeyStartDate, ekeyEndDate } = this.data;
    if (ekeyStartDate > 0 && start < ekeyStartDate) {
      wx.showToast({ title: '生效时间不能早于您自身的生效时间', icon: 'none' });
      return null;
    }
    if (ekeyEndDate > 0 && end > ekeyEndDate) {
      wx.showToast({ title: `失效时间不能晚于 ${this.fmtFullDateTime(ekeyEndDate)}`, icon: 'none' });
      return null;
    }
    return { startDate: start, endDate: end };
  },

  async onAdd() {
    if (this.data.adding) return;
    if (!this.data.lockData) {
      wx.showToast({ title: '锁数据不可用，请重进页面', icon: 'none' });
      return;
    }
    const ts = this.buildTimestamps();
    if (!ts) return;

    this.setData({ adding: true, stepText: '请靠近锁...' });

    try {
      const result = await ttlock.addICCard(
        this.data.lockData,
        ts.startDate,
        ts.endDate,
        (step) => {
          if (step && step.type === 2) {
            this.setData({ stepText: '请在锁上刷一次卡' });
          }
        }
      );

      if (!result || result.errorCode !== 0 || result.cardNum == null) {
        throw new Error((result && result.errorMsg) || '录入失败');
      }

      this.setData({ stepText: '正在同步到云端...' });
      await icCardApi.add({
        lockId: this.data.lockId,
        cardNumber: String(result.cardNum),
        cardName: this.data.cardName || 'IC 卡',
        startDate: ts.startDate,
        endDate: ts.endDate
      });

      wx.showToast({ title: '添加成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 800);
    } catch (err) {
      console.error('Add IC card failed:', err);
      wx.showToast({ title: err.message || '添加失败', icon: 'none' });
    } finally {
      this.setData({ adding: false, stepText: '' });
      ttlock.finishOperations().catch(() => {});
    }
  }
});
