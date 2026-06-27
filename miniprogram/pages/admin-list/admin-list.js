const { keyApi } = require('../../utils/api');

Page({
  data: {
    lockId: null,
    lockName: '',
    admins: [],
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

  onLoad(options) {
    if (options.lockId) {
      this.setData({ lockId: parseInt(options.lockId) });
    }
    if (options.lockName) {
      this.setData({ lockName: decodeURIComponent(options.lockName) });
    }
  },

  onShow() {
    this.loadAdmins();
  },

  async loadAdmins() {
    const { lockId } = this.data;
    if (!lockId || this.data.loading) return;
    this.setData({ loading: true });
    try {
      wx.showLoading({ title: '加载中...' });
      const list = await keyApi.getAdminList(lockId);
      const now = Date.now();
      const decorated = (list || []).map(k => {
        const isPermanent = !k.startDate && !k.endDate;
        const expired = !isPermanent && k.endDate && k.endDate < now;
        return {
          ...k,
          rangeText: isPermanent
            ? '永久有效'
            : `${this.formatRange(k.startDate)} - ${this.formatRange(k.endDate)}`,
          statusText: expired ? '已失效' : '有效',
          expired
        };
      });
      this.setData({ admins: decorated, loading: false });
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      this.setData({ loading: false });
      console.error('Load admin keys failed:', err);
    }
  },

  goAddAdmin() {
    const { lockId, lockName } = this.data;
    wx.navigateTo({
      url: `/pages/admin-send/admin-send?lockId=${lockId}&lockName=${encodeURIComponent(lockName || '')}`
    });
  },

  onAdminClick(e) {
    const index = e.currentTarget.dataset.index;
    const admin = this.data.admins[index];
    if (!admin) return;
    const name = admin.keyName || admin.receiverUsername || '该管理员';
    wx.showModal({
      title: '删除管理员',
      content: `确定删除「${name}」的管理员权限吗？删除后对方将失去这把锁的所有访问权限。`,
      confirmColor: '#ff5252',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          wx.showLoading({ title: '删除中...' });
          await keyApi.delete(admin.id);
          wx.hideLoading();
          wx.showToast({ title: '删除成功', icon: 'success' });
          this.loadAdmins();
        } catch (err) {
          wx.hideLoading();
          console.error('Delete admin failed:', err);
        }
      }
    });
  }
});
