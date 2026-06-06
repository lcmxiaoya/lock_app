const app = getApp();
const { pwdApi, lockApi } = require('../../utils/api');
const ttlock = require('../../utils/ttlock');

Page({
  data: {
    lockId: null,
    selectedType: 1,
    pwdName: '',
    startDateStr: '',
    endDateStr: '',
    startDate: 0,
    endDate: 0,
    generatedPwd: '',
    generatedPwdId: null,
    pwdTypeName: '单次',
    isPermanent: false,
    customPwd: '',
    statusBarHeight: 0,
    navBarHeight: 0
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
  },

  onSelectType(e) {
    const type = parseInt(e.currentTarget.dataset.type);
    this.setData({
      selectedType: type,
      pwdTypeName: this.getPwdTypeName(type),
      generatedPwd: '',
      generatedPwdId: null,
      startDateStr: '',
      endDateStr: '',
      startDate: 0,
      endDate: 0,
      isPermanent: false,
      customPwd: ''
    });
  },

  onPwdNameInput(e) {
    this.setData({ pwdName: e.detail.value });
  },

  onPermanentChange(e) {
    this.setData({ isPermanent: e.detail.value });
  },

  onCustomPwdInput(e) {
    this.setData({ customPwd: e.detail.value });
  },

  onPickStartDate() {
    const that = this;
    wx.showActionSheet({
      itemList: ['选择日期和时间'],
      success() {
        that.showDateTimePicker('startDate');
      }
    });
  },

  onPickEndDate() {
    const that = this;
    wx.showActionSheet({
      itemList: ['选择日期和时间'],
      success() {
        that.showDateTimePicker('endDate');
      }
    });
  },

  showDateTimePicker(field) {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');

    wx.showModal({
      title: '选择日期',
      editable: true,
      placeholderText: '格式: YYYY-MM-DD HH:MM',
      content: `${y}-${m}-${d} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
      success: (res) => {
        if (res.confirm && res.content) {
          const parts = res.content.trim().split(' ');
          if (parts.length === 2) {
            const dateStr = parts[0];
            const timeStr = parts[1];
            const dateObj = new Date(`${dateStr}T${timeStr}:00`);
            if (!isNaN(dateObj.getTime())) {
              const ts = dateObj.getTime();
              const displayStr = `${dateStr} ${timeStr}`;
              if (field === 'startDate') {
                this.setData({ startDateStr: displayStr, startDate: ts });
              } else {
                this.setData({ endDateStr: displayStr, endDate: ts });
              }
              return;
            }
          }
          wx.showToast({ title: '格式错误，请使用 YYYY-MM-DD HH:MM', icon: 'none' });
        }
      }
    });
  },

  async onGenerate() {
    const { lockId, selectedType, pwdName, startDate, endDate, isPermanent, customPwd } = this.data;

    if (!lockId) {
      wx.showToast({ title: '锁信息缺失', icon: 'none' });
      return;
    }

    const now = Date.now();
    let finalStartDate = startDate;
    let finalEndDate = endDate;

    if (selectedType === 3) {
      if (!startDate || !endDate) {
        wx.showToast({ title: '请选择有效期', icon: 'none' });
        return;
      }
      if (endDate <= startDate) {
        wx.showToast({ title: '结束时间必须晚于开始时间', icon: 'none' });
        return;
      }
    } else if (selectedType === 1) {
      finalStartDate = now;
      finalEndDate = now + 6 * 60 * 60 * 1000;
    } else if (selectedType === 2) {
      finalStartDate = now;
      finalEndDate = 0;
    } else if (selectedType === 0) {
      if (isPermanent) {
        finalStartDate = now;
        finalEndDate = 0;
      } else {
        if (!startDate || !endDate) {
          wx.showToast({ title: '请选择有效期', icon: 'none' });
          return;
        }
        if (endDate <= startDate) {
          wx.showToast({ title: '结束时间必须晚于开始时间', icon: 'none' });
          return;
        }
      }
    } else if (selectedType === 6) {
      finalStartDate = now;
      finalEndDate = 0;
    }

    if (selectedType === 0) {
      if (!customPwd || customPwd.length < 6 || customPwd.length > 9) {
        wx.showToast({ title: '请输入6-9位数字密码', icon: 'none' });
        return;
      }
      if (!/^\d+$/.test(customPwd)) {
        wx.showToast({ title: '密码只能包含数字', icon: 'none' });
        return;
      }

      try {
        wx.showLoading({ title: '获取锁数据...' });
        const lockDetail = await lockApi.getDetail(lockId);
        const lockData = lockDetail.lockData;
        if (!lockData) {
          wx.hideLoading();
          wx.showToast({ title: '锁数据不可用', icon: 'none' });
          return;
        }

        wx.showLoading({ title: '蓝牙设置密码中...' });
        const bleResult = await ttlock.createCustomPasscode(lockData, customPwd, finalStartDate, finalEndDate);
        if (bleResult.errorCode !== 0) {
          wx.hideLoading();
          wx.showToast({ title: '蓝牙设置失败', icon: 'none' });
          return;
        }

        const keyboardPwdId = bleResult.keyboardPwdId;
        const newLockData = bleResult.lockData;

        wx.showLoading({ title: '同步到云端...' });
        const result = await pwdApi.addCustom({
          lockId,
          keyboardPwd: customPwd,
          keyboardPwdId: keyboardPwdId,
          pwdName: pwdName || undefined,
          pwdType: isPermanent ? 2 : 3,
          startDate: finalStartDate,
          endDate: finalEndDate
        });

        if (newLockData) {
          await lockApi.updateData(lockId, newLockData).catch(() => {});
        }

        this.setData({
          generatedPwd: customPwd,
          generatedPwdId: result.pwdId
        });

        wx.hideLoading();
        wx.showToast({ title: '设置成功', icon: 'success' });
      } catch (err) {
        wx.hideLoading();
        console.error('Add custom password failed:', err);
        wx.showToast({ title: '设置失败: ' + (err.errMsg || err.message || '未知错误'), icon: 'none' });
      } finally {
        ttlock.finishOperations().catch(() => {});
      }
    } else {
      try {
        wx.showLoading({ title: '生成中...' });

        const result = await pwdApi.generate({
          lockId,
          pwdType: selectedType,
          pwdName: pwdName || undefined,
          startDate: finalStartDate,
          endDate: finalEndDate
        });

        this.setData({
          generatedPwd: result.keyboardPwd,
          generatedPwdId: result.pwdId
        });

        wx.hideLoading();
        wx.showToast({ title: '生成成功', icon: 'success' });
      } catch (err) {
        wx.hideLoading();
        console.error('Generate password failed:', err);
      }
    }
  },

  copyPwd() {
    const { generatedPwd } = this.data;
    if (!generatedPwd) return;
    wx.setClipboardData({
      data: generatedPwd,
      success() {
        wx.showToast({ title: '已复制', icon: 'success' });
      }
    });
  },

  onGoBack() {
    wx.navigateBack();
  },

  getPwdTypeName(type) {
    const names = {
      0: '自定义',
      1: '单次',
      2: '永久',
      3: '限时',
      6: '循环'
    };
    return names[type] || '未知';
  }
});
