const app = getApp();

/**
 * Request wrapper with token
 */
function request(options) {
  return new Promise((resolve, reject) => {
    const token = app.globalData.token;
    
    wx.request({
      url: `${app.globalData.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      success(res) {
        if (res.statusCode === 200) {
          const data = res.data;
          if (data.code === 0) {
            resolve(data.data);
          } else {
            wx.showToast({
              title: data.message || 'Request failed',
              icon: 'none'
            });
            reject(data);
          }
        } else if (res.statusCode === 401) {
          // Token expired, redirect to login
          app.clearLoginInfo();
          wx.redirectTo({
            url: '/pages/login/login'
          });
          reject({ code: 1002, message: 'Token expired' });
        } else {
          wx.showToast({
            title: 'Network error',
            icon: 'none'
          });
          reject({ code: res.statusCode, message: 'Network error' });
        }
      },
      fail(err) {
        wx.showToast({
          title: 'Network error',
          icon: 'none'
        });
        reject(err);
      }
    });
  });
}

/**
 * User API
 */
const userApi = {
  sendCode(username) {
    return request({
      url: `/api/user/sendCode?username=${username}`,
      method: 'POST'
    });
  },
  
  register(data) {
    return request({
      url: '/api/user/register',
      method: 'POST',
      data
    });
  },
  
  login(data) {
    return request({
      url: '/api/user/login',
      method: 'POST',
      data
    });
  },
  
  getInfo() {
    return request({
      url: '/api/user/info'
    });
  }
};

/**
 * Lock API
 */
const lockApi = {
  getList(pageNo = 1, pageSize = 20) {
    return request({
      url: `/api/lock/list?pageNo=${pageNo}&pageSize=${pageSize}`
    });
  },
  
  getDetail(lockId) {
    return request({
      url: `/api/lock/detail?lockId=${lockId}`
    });
  },
  
  add(data) {
    return request({
      url: '/api/lock/add',
      method: 'POST',
      data
    });
  },
  
  delete(lockId, password) {
    return request({
      url: '/api/lock/delete',
      method: 'POST',
      data: { lockId, password }
    });
  },
  
  updateData(lockId, lockData) {
    return request({
      url: '/api/lock/updateData',
      method: 'POST',
      data: { lockId, lockData }
    });
  },

  getUnlockData(lockId) {
    return request({
      url: `/api/lock/unlockData?lockId=${lockId}`
    });
  }
};

/**
 * Key API
 */
const keyApi = {
  getList(lockId, pageNo = 1, pageSize = 20) {
    let url = lockId
      ? `/api/key/list?lockId=${lockId}&pageNo=${pageNo}&pageSize=${pageSize}`
      : `/api/key/list?pageNo=${pageNo}&pageSize=${pageSize}`;
    return request({ url });
  },
  
  getDetail(keyId) {
    return request({
      url: `/api/key/detail?keyId=${keyId}`
    });
  },
  
  send(data) {
    return request({
      url: '/api/key/send',
      method: 'POST',
      data
    });
  },
  
  delete(keyId) {
    return request({
      url: '/api/key/delete',
      method: 'POST',
      data: { keyId }
    });
  }
};

/**
 * Password API
 */
const pwdApi = {
  getList(lockId, pageNo = 1, pageSize = 20) {
    return request({
      url: `/api/pwd/list?lockId=${lockId}&pageNo=${pageNo}&pageSize=${pageSize}`
    });
  },

  generate(data) {
    return request({
      url: '/api/pwd/generate',
      method: 'POST',
      data
    });
  },

  addCustom(data) {
    return request({
      url: '/api/pwd/custom/add',
      method: 'POST',
      data
    });
  },

  delete(lockId, pwdId) {
    return request({
      url: '/api/pwd/delete',
      method: 'POST',
      data: { lockId, pwdId }
    });
  },

  reset(data) {
    return request({
      url: '/api/pwd/reset',
      method: 'POST',
      data
    });
  },

  change(data) {
    return request({
      url: '/api/pwd/change',
      method: 'POST',
      data
    });
  }
};

/**
 * Record API
 */
const recordApi = {
  upload(data) {
    return request({
      url: '/api/record/upload',
      method: 'POST',
      data
    });
  },
  
  getList(lockId, pageNo = 1, pageSize = 20, keyboardPwd, keyId) {
    let url = `/api/record/list?lockId=${lockId}&pageNo=${pageNo}&pageSize=${pageSize}`;
    if (keyboardPwd) {
      url += `&keyboardPwd=${encodeURIComponent(keyboardPwd)}`;
    }
    if (keyId) {
      url += `&keyId=${keyId}`;
    }
    return request({ url });
  },

  getListByKey(lockId, keyUserId, pageNo = 1, pageSize = 20) {
    return request({
      url: `/api/record/list?lockId=${lockId}&keyUserId=${keyUserId}&pageNo=${pageNo}&pageSize=${pageSize}`
    });
  },

  sync(lockId, log) {
    return request({
      url: '/api/record/sync',
      method: 'POST',
      data: { lockId, log }
    });
  }
};

module.exports = {
  request,
  userApi,
  lockApi,
  keyApi,
  pwdApi,
  recordApi
};
