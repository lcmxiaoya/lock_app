let plugin = null

function getPlugin() {
  if (!plugin) {
    plugin = requirePlugin('ttlock-plugin')
  }
  return plugin
}

function openBluetoothAdapter() {
  return new Promise((resolve, reject) => {
    wx.getSystemInfo({
      success: (res) => {
        if (res.platform === 'devtools') {
          reject({ errCode: -1, errMsg: '模拟器不支持蓝牙，请使用真机调试' })
          return
        }
        resolve({ success: true })
      },
      fail: reject
    })
  })
}

function startSearch(callback) {
  const p = getPlugin()
  if (p && typeof p.startScanBleDevice === 'function') {
    p.startScanBleDevice((rawDevice) => {
      const lockDevice = {
        lockName: rawDevice.deviceName || rawDevice.lockName || '未知设备',
        lockMac: rawDevice.MAC || rawDevice.lockMac || rawDevice.deviceId || '',
        deviceId: rawDevice.deviceId || rawDevice.MAC || '',
        rssi: rawDevice.rssi || 0,
        isSettingMode: rawDevice.isSettingMode || false,
        electricQuantity: rawDevice.electricQuantity || 0,
        protocolType: (rawDevice.lockVersion && rawDevice.lockVersion.protocolType) || 0,
        protocolVersion: (rawDevice.lockVersion && rawDevice.lockVersion.protocolVersion) || 0,
        scene: (rawDevice.lockVersion && rawDevice.lockVersion.scene) || 0,
        _rawDevice: rawDevice
      }
      if (callback) callback(lockDevice)
    })
  }
}

function stopSearch() {
  return new Promise((resolve, reject) => {
    const p = getPlugin()
    if (p && typeof p.stopScanBleDevice === 'function') {
      p.stopScanBleDevice({ success: resolve, fail: reject })
    } else {
      wx.stopBluetoothDevicesDiscovery({ success: resolve, fail: reject })
    }
  })
}

function initLock(lockDevice) {
  return new Promise((resolve, reject) => {
    if (!lockDevice.isSettingMode) {
      reject(new Error('锁不处于设置模式'))
      return
    }
    const p = getPlugin()
    if (!p || typeof p.initLock !== 'function') {
      reject(new Error('插件未加载'))
      return
    }
    const deviceToInit = lockDevice._rawDevice || lockDevice
    p.initLock({ deviceFromScan: deviceToInit })
      .then((result) => {
        if (result.errorCode === 0) resolve(result)
        else reject(new Error(result.errorMsg || '初始化失败'))
      })
      .catch(reject)
  })
}

function finishOperations() {
  return new Promise((resolve, reject) => {
    const p = getPlugin()
    if (p && typeof p.finishOperations === 'function') {
      p.finishOperations((result) => {
        if (result && result.errorCode === 0) resolve(result)
        else reject(result)
      })
    } else {
      resolve({ success: true })
    }
  })
}

function unlock(lockData) {
  const p = getPlugin()
  return p.controlLock({
    controlAction: 3,
    lockData,
    serverTime: Date.now()
  })
}

function lockDevice(lockData) {
  const p = getPlugin()
  return p.controlLock({
    controlAction: 6,
    lockData,
    serverTime: Date.now()
  })
}

function resetLock(lockData) {
  const p = getPlugin()
  return p.resetLock({ lockData })
}

function createCustomPasscode(lockData, passcode, startDate, endDate) {
  const p = getPlugin()
  return p.createCustomPasscode({ passcode, startDate, endDate, lockData })
}

function deletePasscode(lockData, passcode) {
  const p = getPlugin()
  return p.deletePasscode({ passcode, lockData })
}

function resetPasscode(lockData) {
  const p = getPlugin()
  return p.resetPasscode({ lockData })
}

function getAllValidPasscode(lockData) {
  const p = getPlugin()
  return p.getAllValidPasscode({ lockData })
}

function getOperationLog(lockData, logType = 2) {
  const p = getPlugin()
  return p.getOperationLog({ logType, lockData })
}

module.exports = {
  openBluetoothAdapter,
  startSearch,
  stopSearch,
  initLock,
  unlock,
  lock: lockDevice,
  resetLock,
  createCustomPasscode,
  deletePasscode,
  resetPasscode,
  getAllValidPasscode,
  getOperationLog,
  finishOperations
}
