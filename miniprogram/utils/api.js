const app = getApp();

/**
 * Request wrapper — 本地用 wx.request，线上用 wx.cloud.callContainer
 */
function request(options) {
  return new Promise((resolve, reject) => {
    const token = app.globalData.token;

    if (app.useCloud) {
      // 微信云托管模式
      const header = {
        'Content-Type': 'application/json',
        'X-WX-SERVICE': app.cloudHosting.service
      };
      if (token) {
        header['Authorization'] = `Bearer ${token}`;
      }

      wx.cloud.callContainer({
        config: { env: app.cloudHosting.env },
        path: options.url,
        method: options.method || 'GET',
        header,
        data: options.data || {},
        success(res) { handleResponse(res, resolve, reject) },
        fail(err) { handleError(reject, err) }
      });
    } else {
      // 本地开发模式
      const header = { 'Content-Type': 'application/json' };
      if (token) {
        header['Authorization'] = `Bearer ${token}`;
      }

      wx.request({
        url: `${app.globalData.baseUrl}${options.url}`,
        method: options.method || 'GET',
        data: options.data || {},
        header,
        success(res) { handleResponse(res, resolve, reject) },
        fail(err) { handleError(reject, err) }
      });
    }
  });
}

function handleResponse(res, resolve, reject) {
  if (res.statusCode === 200) {
    const data = res.data;
    if (data.code === 0) {
      resolve(data.data);
    } else {
      wx.showToast({ title: data.message || 'Request failed', icon: 'none' });
      reject(data);
    }
  } else if (res.statusCode === 401) {
    // 401 分两种：登录态失效（带 token 的请求） vs 接口未在白名单（无 token 请求）。
    // 后者不应清登录态/跳登录页，否则会把"后端未部署"等服务端问题伪装成登录过期。
    const hadToken = !!app.globalData.token;
    if (hadToken) {
      app.clearLoginInfo();
      wx.redirectTo({ url: '/pages/login/login' });
      reject({ code: 1002, message: '登录已过期，请重新登录' });
    } else {
      const msg = (res.data && res.data.message) || '接口未授权（401），请联系管理员';
      wx.showToast({ title: msg, icon: 'none' });
      reject({ code: 401, message: msg });
    }
  } else {
    wx.showToast({ title: 'Network error', icon: 'none' });
    reject({ code: res.statusCode, message: 'Network error' });
  }
}

function handleError(reject, err) {
  wx.showToast({ title: 'Network error', icon: 'none' });
  reject(err);
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

  wxLogin(data) {
    return request({
      url: '/api/user/wxLogin',
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
  
  delete(lockId, confirmText) {
    return request({
      url: '/api/lock/delete',
      method: 'POST',
      data: { lockId, confirmText }
    });
  },
  
  updateData(lockId, lockData) {
    return request({
      url: '/api/lock/updateData',
      method: 'POST',
      data: { lockId, lockData }
    });
  },

  updateName(lockId, lockName) {
    return request({
      url: '/api/lock/updateName',
      method: 'POST',
      data: { lockId, lockName }
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

  /** 授权管理员：等价于"先 send 再 authorize"，由后端组合 */
  sendAdmin(data) {
    return request({
      url: '/api/key/sendAdmin',
      method: 'POST',
      data
    });
  },

  /** 获取该锁所有"管理员"钥匙；仅锁拥有者可查 */
  getAdminList(lockId) {
    return request({
      url: `/api/key/adminList?lockId=${lockId}`
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

/**
 * IC Card API（仅锁拥有者可操作）
 */
const icCardApi = {
  getList(lockId, pageNo = 1, pageSize = 20) {
    return request({
      url: `/api/icCard/list?lockId=${lockId}&pageNo=${pageNo}&pageSize=${pageSize}`
    });
  },
  add(data) {
    return request({ url: '/api/icCard/add', method: 'POST', data });
  },
  delete(recordId) {
    return request({ url: '/api/icCard/delete', method: 'POST', data: { recordId } });
  },
  modifyValidity(data) {
    return request({ url: '/api/icCard/modifyValidity', method: 'POST', data });
  }
};

/**
 * Fingerprint API（仅锁拥有者可操作）
 */
const fingerprintApi = {
  getList(lockId, pageNo = 1, pageSize = 20) {
    return request({
      url: `/api/fingerprint/list?lockId=${lockId}&pageNo=${pageNo}&pageSize=${pageSize}`
    });
  },
  add(data) {
    return request({ url: '/api/fingerprint/add', method: 'POST', data });
  },
  delete(recordId) {
    return request({ url: '/api/fingerprint/delete', method: 'POST', data: { recordId } });
  },
  modifyValidity(data) {
    return request({ url: '/api/fingerprint/modifyValidity', method: 'POST', data });
  }
};

module.exports = {
  request,
  userApi,
  lockApi,
  keyApi,
  pwdApi,
  recordApi,
  icCardApi,
  fingerprintApi
};
