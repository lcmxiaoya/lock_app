const { lockApi, fingerprintApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

// 1=周一 ... 7=周日
const WEEK_DAYS = [
  { value: 1, label: '一' },
  { value: 2, label: '二' },
  { value: 3, label: '三' },
  { value: 4, label: '四' },
  { value: 5, label: '五' },
  { value: 6, label: '六' },
  { value: 7, label: '日' }
];

Page({
  data: {
    lockId: null,
    lockName: '',
    fingerName: '',
    selectedType: 'permanent',          // 'timed' | 'permanent' | 'cyclic'
    startDate: '',
    endDate: '',
    startTime: '',
    endTime: '',
    // 周期
    cyclicDays: [],                     // [1..7]
    cyclicStartTime: '08:00',
    cyclicEndTime: '18:00',
    weekDays: WEEK_DAYS,                // 模板用
    // 来自 lock-control：admin 自己的 ekey 有效期（>0 时受限）
    ekeyStartDate: 0,
    ekeyEndDate: 0,
    permanentDisabled: false,
    validityHint: '',
    lockData: '',
    adding: false,
    stepText: '',
    progress: ''                        // "1/3" 进度
  },

  onLoad(options) {
    if (options.lockId) this.setData({ lockId: parseInt(options.lockId) });
    if (options.lockName) this.setData({ lockName: decodeURIComponent(options.lockName) });
    const ekeyEnd = parseInt(options.ekeyEndDate || 0);
    this.setData({
      ekeyStartDate: parseInt(options.ekeyStartDate || 0),
      ekeyEndDate: ekeyEnd,
      permanentDisabled: ekeyEnd > 0,
      validityHint: ekeyEnd > 0
        ? `您是该锁的管理员，限时模式的失效时间不能晚于 ${this.fmtFullDateTime(ekeyEnd)}`
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

  // 通用
  onSelectType(e) {
    const t = e.currentTarget.dataset.type;
    if (t === 'permanent' && this.data.permanentDisabled) {
      wx.showToast({ title: '您自身有截止时间，无法设置永久', icon: 'none' });
      return;
    }
    this.setData({ selectedType: t });
  },
  onFingerNameInput(e) { this.setData({ fingerName: e.detail.value }); },
  onStartDateChange(e) { this.setData({ startDate: e.detail.value }); },
  onEndDateChange(e) { this.setData({ endDate: e.detail.value }); },
  onStartTimeChange(e) { this.setData({ startTime: e.detail.value }); },
  onEndTimeChange(e) { this.setData({ endTime: e.detail.value }); },

  // 周期
  onCyclicStartTimeChange(e) { this.setData({ cyclicStartTime: e.detail.value }); },
  onCyclicEndTimeChange(e) { this.setData({ cyclicEndTime: e.detail.value }); },
  onDayToggle(e) {
    const d = parseInt(e.currentTarget.dataset.day);
    const arr = this.data.cyclicDays.slice();
    const i = arr.indexOf(d);
    if (i >= 0) arr.splice(i, 1); else arr.push(d);
    arr.sort();
    this.setData({ cyclicDays: arr });
  },

  async fetchLockData() {
    try {
      const detail = await lockApi.getDetail(this.data.lockId);
      this.setData({ lockData: detail.lockData || '' });
    } catch (err) {
      console.error('fetchLockData failed', err);
    }
  },

  buildPayload() {
    const { selectedType } = this.data;
    if (selectedType === 'permanent') {
      if (this.data.permanentDisabled) {
        wx.showToast({ title: '您自身有截止时间，无法设置永久', icon: 'none' });
        return null;
      }
      return { fingerprintType: 1, startDate: 0, endDate: 0, cyclicConfig: null };
    }
    if (selectedType === 'timed') {
      const start = new Date(`${this.data.startDate.replace(/-/g,'/')} ${this.data.startTime || '00:00'}`).getTime();
      const end   = new Date(`${this.data.endDate.replace(/-/g,'/')} ${this.data.endTime || '00:00'}`).getTime();
      if (isNaN(start) || isNaN(end) || end <= start) {
        wx.showToast({ title: '请检查有效期', icon: 'none' });
        return null;
      }
      // admin 自身 ekey 区间硬约束
      const { ekeyStartDate, ekeyEndDate } = this.data;
      if (ekeyStartDate > 0 && start < ekeyStartDate) {
        wx.showToast({ title: '生效时间不能早于您自身的生效时间', icon: 'none' });
        return null;
      }
      if (ekeyEndDate > 0 && end > ekeyEndDate) {
        wx.showToast({ title: `失效时间不能晚于 ${this.fmtFullDateTime(ekeyEndDate)}`, icon: 'none' });
        return null;
      }
      return { fingerprintType: 1, startDate: start, endDate: end, cyclicConfig: null };
    }
    if (selectedType === 'cyclic') {
      if (!this.data.cyclicDays.length) {
        wx.showToast({ title: '请至少选一天', icon: 'none' });
        return null;
      }
      const [h1, m1] = this.data.cyclicStartTime.split(':').map(Number);
      const [h2, m2] = this.data.cyclicEndTime.split(':').map(Number);
      const startTime = h1 * 60 + m1;
      const endTime = h2 * 60 + m2;
      if (endTime <= startTime) {
        wx.showToast({ title: '结束时间需晚于开始', icon: 'none' });
        return null;
      }
      const arr = this.data.cyclicDays.map(weekDay => ({ startTime, endTime, weekDay }));
      return { fingerprintType: 4, startDate: 0, endDate: 0, cyclicConfig: JSON.stringify(arr) };
    }
    return null;
  },

  async onAdd() {
    if (this.data.adding) return;
    if (!this.data.lockData) {
      wx.showToast({ title: '锁数据不可用，请重进页面', icon: 'none' });
      return;
    }
    const payload = this.buildPayload();
    if (!payload) return;

    this.setData({ adding: true, stepText: '请靠近锁...', progress: '' });

    try {
      const result = await ttlock.addFingerprint(
        this.data.lockData,
        payload.startDate,
        payload.endDate,
        (step) => {
          if (!step) return;
          if (step.type === 2) {
            this.setData({ stepText: '请在锁上按指纹' });
          } else if (step.type === 3) {
            this.setData({
              stepText: '录入中...',
              progress: `${step.currentCount}/${step.totalCount}`
            });
          }
        }
      );

      if (!result || result.errorCode !== 0 || result.fingerprintNum == null) {
        throw new Error((result && result.errorMsg) || '录入失败');
      }

      this.setData({ stepText: '正在同步到云端...', progress: '' });
      await fingerprintApi.add({
        lockId: this.data.lockId,
        fingerprintNumber: String(result.fingerprintNum),
        fingerprintName: this.data.fingerName || '指纹',
        fingerprintType: payload.fingerprintType,
        startDate: payload.startDate,
        endDate: payload.endDate,
        cyclicConfig: payload.cyclicConfig
      });

      wx.showToast({ title: '添加成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 800);
    } catch (err) {
      console.error('Add fingerprint failed:', err);
      wx.showToast({ title: err.message || '添加失败', icon: 'none' });
    } finally {
      this.setData({ adding: false, stepText: '', progress: '' });
      ttlock.finishOperations().catch(() => {});
    }
  }
});
