const { icCardApi, lockApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

Page({
  data: {
    lockId: null,
    lockName: '',
    cards: [],
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
    if (options.lockId) this.setData({ lockId: parseInt(options.lockId) });
    if (options.lockName) this.setData({ lockName: decodeURIComponent(options.lockName) });
    // 透传 admin 的 ekey 有效期到新增页（picker 上限）
    this._ekeyStartDate = parseInt(options.ekeyStartDate || 0);
    this._ekeyEndDate   = parseInt(options.ekeyEndDate   || 0);
  },

  onShow() { this.loadCards(); },

  async loadCards() {
    const { lockId } = this.data;
    if (!lockId || this.data.loading) return;
    this.setData({ loading: true });
    try {
      wx.showLoading({ title: '加载中...' });
      const page = await icCardApi.getList(lockId);
      const now = Date.now();
      const list = (page.list || []).map(c => {
        const isPermanent = !c.startDate && !c.endDate;
        const expired = !isPermanent && c.endDate && c.endDate < now;
        return {
          ...c,
          rangeText: isPermanent
            ? '永久有效'
            : `${this.formatRange(c.startDate)} - ${this.formatRange(c.endDate)}`,
          statusText: expired ? '已失效' : '有效',
          expired
        };
      });
      this.setData({ cards: list, loading: false });
      wx.hideLoading();
    } catch (err) {
      wx.hideLoading();
      this.setData({ loading: false });
      console.error('Load IC cards failed:', err);
    }
  },

  goAdd() {
    const { lockId, lockName } = this.data;
    wx.navigateTo({
      url: `/pages/iccard-add/iccard-add?lockId=${lockId}` +
           `&lockName=${encodeURIComponent(lockName || '')}` +
           `&ekeyStartDate=${this._ekeyStartDate || 0}` +
           `&ekeyEndDate=${this._ekeyEndDate || 0}`
    });
  },

  onCardClick(e) {
    const card = this.data.cards[e.currentTarget.dataset.index];
    if (!card) return;
    wx.showActionSheet({
      itemList: ['修改有效期', '删除该卡'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.modifyCard(card);
        } else if (res.tapIndex === 1) {
          this.deleteCard(card);
        }
      }
    });
  },

  modifyCard(card) {
    const self = this;
    wx.showModal({
      title: '修改有效期',
      content: '该功能需在锁边操作。本期暂仅支持通过删除 + 重新添加的方式更换有效期。',
      showCancel: false
    });
  },

  deleteCard(card) {
    wx.showModal({
      title: '删除 IC 卡',
      content: `确定删除「${card.cardName || card.cardNumber}」吗？请在锁边保持蓝牙连接。`,
      confirmColor: '#ff5252',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          wx.showLoading({ title: '连接锁中...', mask: true });

          const detail = await lockApi.getDetail(this.data.lockId);
          if (!detail.lockData) throw new Error('锁数据不可用');

          wx.showLoading({ title: '删除中...', mask: true });
          const result = await ttlock.deleteICCard(detail.lockData, card.cardNumber);
          if (result.errorCode !== 0) {
            throw new Error(result.errorMsg || '删除失败');
          }

          await icCardApi.delete(card.recordId);

          wx.hideLoading();
          wx.showToast({ title: '删除成功', icon: 'success' });
          this.loadCards();
        } catch (err) {
          wx.hideLoading();
          console.error('Delete IC card failed:', err);
          wx.showToast({ title: err.message || '删除失败', icon: 'none' });
        } finally {
          ttlock.finishOperations().catch(() => {});
        }
      }
    });
  }
});
