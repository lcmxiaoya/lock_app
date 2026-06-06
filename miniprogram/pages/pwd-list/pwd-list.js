const app = getApp();
const { pwdApi, lockApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

Page({
  data: {
    passwords: [],
    allPasswords: [],
    lockId: null,
    pageNo: 1,
    pageSize: 20,
    total: 0,
    loading: false,
    statusBarHeight: 0,
    navBarHeight: 0,
    searchKey: ''
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
    this.loadPasswords();
  },

  onShow() {
    if (this.data.lockId) {
      this.resetAndLoad();
    }
  },

  resetAndLoad() {
    this.setData({ pageNo: 1, passwords: [], allPasswords: [], searchKey: '' });
    this.loadPasswords();
  },

  async loadPasswords() {
    const { lockId, pageNo, pageSize } = this.data;
    if (!lockId || this.data.loading) return;

    this.setData({ loading: true });
    try {
      wx.showLoading({ title: '加载中...' });
      const result = await pwdApi.getList(lockId, pageNo, pageSize);
      const list = (result.list || []).map(item => {
        const now = Date.now();
        item._expired = (item.status === 2) || (item.endDate && item.endDate !== 0 && item.endDate < now && item.pwdType !== 2);
        if (item.keyboardPwdName) {
          try { item.keyboardPwdName = decodeURIComponent(item.keyboardPwdName); } catch (e) {}
        }
        return item;
      });
      const allPasswords = pageNo === 1 ? list : [...this.data.allPasswords, ...list];
      this.setData({
        allPasswords,
        passwords: this.filterList(allPasswords, this.data.searchKey),
        total: result.total,
        loading: false
      });
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      this.setData({ loading: false });
      console.error('Load passwords failed:', err);
    }
  },

  filterList(list, key) {
    if (!key) return list;
    return list.filter(item =>
      (item.keyboardPwdName && item.keyboardPwdName.includes(key)) ||
      (item.keyboardPwd && item.keyboardPwd.includes(key))
    );
  },

  onSearchInput(e) {
    const searchKey = e.detail.value;
    this.setData({
      searchKey,
      passwords: this.filterList(this.data.allPasswords, searchKey)
    });
  },

  onReachBottom() {
    const { pageNo, pageSize, total } = this.data;
    if (this.data.allPasswords.length < total) {
      this.setData({ pageNo: pageNo + 1 });
      this.loadPasswords();
    }
  },

  onPullDownRefresh() {
    this.resetAndLoad();
    wx.stopPullDownRefresh();
  },

  onGoBack() {
    wx.navigateBack();
  },

  async onReset() {
    const { lockId } = this.data;
    if (!lockId) return;

    wx.showModal({
      title: '确认重置',
      content: '重置后所有键盘密码将失效，需要重新生成。此操作不可恢复，确定继续？',
      success: async (res) => {
        if (!res.confirm) return;

        try {
          wx.showLoading({ title: '获取锁数据...' });
          const lockDetail = await lockApi.getDetail(lockId);
          const lockData = lockDetail.lockData;

          wx.showLoading({ title: '蓝牙重置密码中...' });
          const resetResult = await ttlock.resetPasscode(lockData);
          const newLockData = resetResult.lockData;

          wx.showLoading({ title: '同步到云端...' });
          await pwdApi.reset({
            lockId: lockId,
            newLockData: newLockData,
            pwdInfo: '',
            timestamp: Date.now()
          });

          wx.hideLoading();
          wx.showToast({ title: '重置成功，所有旧密码已失效', icon: 'success' });
          this.resetAndLoad();
        } catch (err) {
          wx.hideLoading();
          console.error('Reset passwords failed:', err);
          wx.showToast({ title: '重置失败: ' + (err.errMsg || err.message || '未知错误'), icon: 'none' });
        } finally {
          ttlock.finishOperations().catch(() => {});
        }
      }
    });
  },

  goGetPassword() {
    wx.navigateTo({
      url: `/pages/pwd-get/pwd-get?lockId=${this.data.lockId}`
    });
  },

  goDetail(e) {
    const item = e.currentTarget.dataset.item;
    const pwdData = encodeURIComponent(JSON.stringify(item));
    wx.navigateTo({
      url: `/pages/pwd-detail/pwd-detail?pwdData=${pwdData}&lockId=${this.data.lockId}`
    });
  },

  formatDate(ts) {
    if (!ts || ts === 0) return '';
    const date = new Date(ts);
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const h = String(date.getHours()).padStart(2, '0');
    const min = String(date.getMinutes()).padStart(2, '0');
    return `${y}.${m}.${d} ${h}:${min}`;
  }
});
