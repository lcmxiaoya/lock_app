Component({
  data: {
    selected: 0,
    color: "#999",
    selectedColor: "#2979ff",
    list: [
      {
        "pagePath": "/pages/lock-list/lock-list",
        "text": "首页",
        "iconPath": "/images/home.png",
        "selectedIconPath": "/images/home-active.png"
      },
      {
        "pagePath": "/pages/profile/profile",
        "text": "我的",
        "iconPath": "/images/user.png",
        "selectedIconPath": "/images/user-active.png"
      }
    ]
  },
  methods: {
    switchTab(e) {
      const index = e.currentTarget.dataset.index;
      const url = this.data.list[index].pagePath;
      wx.switchTab({ url });
    }
  }
})
