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

/**
 * 把插件返回的原始设备对象归一化成业务对象。
 * 保留所有 UI/initLock 需要的字段（isSettingMode、lockVersion、rssi、updatedTime 等）。
 */
function normalizeDevice(rawDevice) {
  if (!rawDevice) return null
  const lockVersion = rawDevice.lockVersion || {}
  return {
    lockName: rawDevice.deviceName || rawDevice.lockName || '未知设备',
    lockMac: rawDevice.MAC || rawDevice.lockMac || rawDevice.deviceId || '',
    deviceId: rawDevice.deviceId || rawDevice.MAC || '',
    rssi: rawDevice.rssi || 0,
    isSettingMode: !!rawDevice.isSettingMode,
    isTouch: !!rawDevice.isTouch,
    electricQuantity: rawDevice.electricQuantity || 0,
    updatedTime: rawDevice.updatedTime || 0,
    protocolType: lockVersion.protocolType || 0,
    protocolVersion: lockVersion.protocolVersion || 0,
    scene: lockVersion.scene || 0,
    _rawDevice: rawDevice
  }
}

/**
 * 开始扫描。回调签名对齐插件官方：
 *   onScan(deviceFromScan, deviceFromScanList)
 * 第二个参数是插件按"添加状态 + 信号强度"排序后的整张表，业务端直接整表 setData 即可。
 */
function startSearch(onScan, onFail) {
  const p = getPlugin()
  if (!p || typeof p.startScanBleDevice !== 'function') {
    if (onFail) onFail({ errorCode: -1, errorMsg: '插件未加载' })
    return
  }
  p.startScanBleDevice(
    (rawDevice, rawList) => {
      const device = normalizeDevice(rawDevice)
      const list = Array.isArray(rawList) ? rawList.map(normalizeDevice).filter(Boolean) : []
      if (onScan) onScan(device, list)
    },
    (err) => {
      if (onFail) onFail(err || { errorCode: -1, errorMsg: '扫描失败' })
    }
  )
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

/**
 * 停止扫描 + 断开已建立的蓝牙连接。
 * 3.1.0 之后官方 demo 在退出页面时使用此接口确保蓝牙资源完全释放。
 */
function stopAllOperations() {
  const p = getPlugin()
  if (p && typeof p.stopAllOperations === 'function') {
    return Promise.resolve(p.stopAllOperations()).catch(() => {})
  }
  return stopSearch().catch(() => {})
}

function initLock(lockDevice) {
  return new Promise((resolve, reject) => {
    if (!lockDevice || !lockDevice.isSettingMode) {
      reject(new Error('锁不处于设置模式'))
      return
    }
    const p = getPlugin()
    if (!p || typeof p.initLock !== 'function') {
      reject(new Error('插件未加载'))
      return
    }
    const deviceToInit = lockDevice._rawDevice || lockDevice
    p.initLock({ deviceFromScan: deviceToInit, serverTime: Date.now() })
      .then((result) => {
        if (result && result.errorCode === 0) resolve(result)
        else reject(new Error((result && result.errorMsg) || '初始化失败'))
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

// =================== IC 卡 ===================

/**
 * 添加 IC 卡。
 *
 * 流程：调用此方法 → 插件回调 type=2 时表示"已进入添加模式，请在锁上刷卡" →
 * 真实刷卡后 resolve 拿到 cardNumber。业务层应在 callback 里做引导文案。
 *
 * @param lockData     管理员锁数据
 * @param startDate    生效时间戳（ms），0 = 永久
 * @param endDate      失效时间戳（ms），0 = 永久
 * @param onStep       中间步骤回调 (result) => void，result.type=2 时弹"请刷卡"
 */
function addICCard(lockData, startDate, endDate, onStep) {
  const p = getPlugin()
  return p.addICCard({
    lockData,
    startDate,
    endDate,
    callback: onStep || (() => {})
  })
}

function deleteICCard(lockData, cardNumber) {
  const p = getPlugin()
  return p.deleteICCard({ lockData, cardNum: String(cardNumber) })
}

function modifyICCardValidity(lockData, cardNumber, startDate, endDate) {
  const p = getPlugin()
  return p.modifyICCardValidityPeriod({
    lockData,
    cardNum: String(cardNumber),
    startDate,
    endDate
  })
}

// =================== 指纹 ===================

/**
 * 录入指纹。回调 type=2 提示按指纹；type=3 携带 currentCount/totalCount 进度。
 */
function addFingerprint(lockData, startDate, endDate, onStep) {
  const p = getPlugin()
  return p.addFingerprint({
    lockData,
    startDate,
    endDate,
    callback: onStep || (() => {})
  })
}

function deleteFingerprint(lockData, fingerprintNumber) {
  const p = getPlugin()
  return p.deleteFingerprint({ lockData, fingerprintNum: String(fingerprintNumber) })
}

function modifyFingerprintValidity(lockData, fingerprintNumber, startDate, endDate) {
  const p = getPlugin()
  return p.modifyFingerprintValidityPeriod({
    lockData,
    fingerprintNum: String(fingerprintNumber),
    startDate,
    endDate
  })
}

module.exports = {
  openBluetoothAdapter,
  startSearch,
  stopSearch,
  stopAllOperations,
  initLock,
  unlock,
  lock: lockDevice,
  resetLock,
  createCustomPasscode,
  deletePasscode,
  resetPasscode,
  getAllValidPasscode,
  getOperationLog,
  finishOperations,
  addICCard,
  deleteICCard,
  modifyICCardValidity,
  addFingerprint,
  deleteFingerprint,
  modifyFingerprintValidity
}
