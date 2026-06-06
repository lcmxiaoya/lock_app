const app = getApp();
const { recordApi } = require('../../utils/api');

const PASSWORD_TYPES = [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 38, 53, 92, 93];
const APP_BLE_TYPES = [1, 26, 28, 41, 52, 75, 76];

Page({
  data: {
    records: [],
    groupedRecords: [],
    lockId: null,
    keyboardPwd: null,
    keyId: null,
    pageNo: 1,
    pageSize: 20,
    hasMore: true
  },

  onLoad(options) {
    if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
    }
    if (options.keyboardPwd) {
      this.setData({ keyboardPwd: decodeURIComponent(options.keyboardPwd) });
    }
    if (options.keyId) {
      this.setData({ keyId: parseInt(options.keyId) });
    }
    this.loadRecords();
  },

  async loadRecords() {
    const { lockId, pageNo, pageSize, keyboardPwd, keyId } = this.data;
    if (!lockId) return;

    try {
      wx.showLoading({ title: '加载中...' });
      const result = await recordApi.getList(lockId, pageNo, pageSize, keyboardPwd, keyId);

      const newRecords = result.list.map(r => {
        const d = new Date(r.recordTime);
        const isPwdRecord = PASSWORD_TYPES.includes(r.recordType);
        const isAppType = APP_BLE_TYPES.includes(r.recordType);
        const isFailed = r.result === 'fail';

        let title = r.actionName;
        if (isPwdRecord) {
          if (isFailed) {
            title = r.keyboardPwd || '***';
          } else {
            title = r.passwordName || this.maskPassword(r.keyboardPwd) || '***';
          }
        }

        return {
          ...r,
          dateStr: this.formatDate(d),
          timeStr: this.formatTime(d),
          title: title,
          description: r.actionName,
          isAppUnlock: isAppType,
          isPasswordRecord: isPwdRecord,
          isFailed: isFailed
        };
      });

      const allRecords = pageNo === 1 ? newRecords : [...this.data.records, ...newRecords];

      this.setData({
        records: allRecords,
        groupedRecords: this.groupByDate(allRecords),
        hasMore: result.list.length === pageSize
      });

      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      console.error('Load records failed:', err);
    }
  },

  maskPassword(pwd) {
    if (!pwd) return '***';
    if (pwd.length <= 5) return pwd;
    return pwd.substring(0, 5) + '***';
  },

  groupByDate(records) {
    const groups = {};
    records.forEach(r => {
      const date = r.dateStr;
      if (!groups[date]) {
        groups[date] = { date, records: [] };
      }
      groups[date].records.push(r);
    });
    return Object.values(groups);
  },

  formatDate(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}.${m}.${day}`;
  },

  formatTime(d) {
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    const s = String(d.getSeconds()).padStart(2, '0');
    return `${h}:${m}:${s}`;
  },

  onLoadMore() {
    this.setData({ pageNo: this.data.pageNo + 1 });
    this.loadRecords();
  },

  onPullDownRefresh() {
    this.setData({
      records: [],
      groupedRecords: [],
      pageNo: 1,
      hasMore: true
    });
    this.loadRecords();
    wx.stopPullDownRefresh();
  }
});
