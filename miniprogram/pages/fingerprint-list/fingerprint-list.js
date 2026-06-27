const { fingerprintApi } = require('../../utils/api');

Page({
  data: {
    lockId: null,
    lockName: '',
    fingers: [],
    loading: false
  },

  formatRange(ts) {
    if (!ts || ts === 0) return '';
    const d = new Date(ts);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const h = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    return `${y}.${m}.${day} ${h}:${min}`;
  },

  describeCyclic(cfg) {
    if (!cfg) return '';
    try {
      const arr = JSON.parse(cfg);
      if (!Array.isArray(arr) || arr.length === 0) return '';
      const days = ['一', '二', '三', '四', '五', '六', '日'];
      const groups = {};
      arr.forEach(it => {
        const d = days[(it.weekDay - 1) % 7] || '?';
        if (!groups[d]) groups[d] = it;
      });
      const sample = arr[0];
      const hh1 = String(Math.floor(sample.startTime / 60)).padStart(2, '0');
      const mm1 = String(sample.startTime % 60).padStart(2, '0');
      const hh2 = String(Math.floor(sample.endTime / 60)).padStart(2, '0');
      const mm2 = String(sample.endTime % 60).padStart(2, '0');
      const dayLabels = Object.keys(groups).join('、');
      return `周期 ${dayLabels} ${hh1}:${mm1}-${hh2}:${mm2}`;
    } catch (e) { return ''; }
  },

  onLoad(options) {
    if (options.lockId) this.setData({ lockId: parseInt(options.lockId) });
    if (options.lockName) this.setData({ lockName: decodeURIComponent(options.lockName) });
    // 透传 admin 的 ekey 有效期到新增页（picker 上限）
    this._ekeyStartDate = parseInt(options.ekeyStartDate || 0);
    this._ekeyEndDate   = parseInt(options.ekeyEndDate   || 0);
  },

  onShow() { this.loadFingers(); },

  async loadFingers() {
    const { lockId } = this.data;
    if (!lockId || this.data.loading) return;
    this.setData({ loading: true });
    try {
      wx.showLoading({ title: '加载中...' });
      const page = await fingerprintApi.getList(lockId);
      const now = Date.now();
      const list = (page.list || []).map(f => {
        const isPermanent = (!f.startDate && !f.endDate) && f.fingerprintType === 1;
        const expired = !isPermanent && f.endDate && f.endDate < now;
        const isCyclic = f.fingerprintType === 4;
        let rangeText;
        if (isCyclic) {
          rangeText = this.describeCyclic(f.cyclicConfig) || '周期型';
        } else if (isPermanent) {
          rangeText = '永久有效';
        } else {
          rangeText = `${this.formatRange(f.startDate)} - ${this.formatRange(f.endDate)}`;
        }
        return {
          ...f,
          rangeText,
          typeText: isCyclic ? '周期' : '普通',
          statusText: expired ? '已失效' : '有效',
          expired
        };
      });
      this.setData({ fingers: list, loading: false });
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      this.setData({ loading: false });
      console.error('Load fingerprints failed:', err);
    }
  },

  goAdd() {
    const { lockId, lockName } = this.data;
    wx.navigateTo({
      url: `/pages/fingerprint-add/fingerprint-add?lockId=${lockId}` +
           `&lockName=${encodeURIComponent(lockName || '')}` +
           `&ekeyStartDate=${this._ekeyStartDate || 0}` +
           `&ekeyEndDate=${this._ekeyEndDate || 0}`
    });
  },

  onFingerClick(e) {
    const fp = this.data.fingers[e.currentTarget.dataset.index];
    if (!fp) return;
    wx.showActionSheet({
      itemList: ['修改有效期', '删除该指纹'],
      success: (res) => {
        if (res.tapIndex === 0) {
          wx.showModal({
            title: '修改有效期',
            content: '需在锁边操作。本期建议删除后重新添加。',
            showCancel: false
          });
        } else if (res.tapIndex === 1) {
          this.deleteFinger(fp);
        }
      }
    });
  },

  deleteFinger(fp) {
    wx.showModal({
      title: '删除指纹',
      content: `确定删除「${fp.fingerprintName || fp.fingerprintNumber}」吗？需要在锁边靠近一次。`,
      confirmColor: '#ff5252',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          wx.showLoading({ title: '删除中...', mask: true });
          await fingerprintApi.delete(fp.recordId);
          wx.hideLoading();
          wx.showToast({ title: '已请求云端删除', icon: 'success' });
          this.loadFingers();
        } catch (err) {
          wx.hideLoading();
          console.error('Delete fingerprint failed:', err);
        }
      }
    });
  }
});
