package com.smartlock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlock.dto.RecordUploadRequest;
import com.smartlock.entity.Lock;
import com.smartlock.entity.Record;
import com.smartlock.entity.User;
import com.smartlock.exception.BusinessException;
import com.smartlock.entity.Passcode;
import com.smartlock.repository.PasscodeRepository;
import com.smartlock.repository.LockRepository;
import com.smartlock.repository.RecordRepository;
import com.smartlock.repository.UserRepository;
import com.smartlock.util.TTLockClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

    private final RecordRepository recordRepository;
    private final LockRepository lockRepository;
    private final PasscodeRepository passcodeRepository;
    private final TTLockClient ttLockClient;
    private final UserService userService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void uploadRecord(Long userId, RecordUploadRequest request) {
        Record record = new Record();
        record.setUserId(userId);
        record.setLockId(request.getLockId());
        record.setAction(request.getAction() != null ? request.getAction() : "unlock");
        record.setResult("success");
        record.setRecordTime(request.getRecordTime() != null ? request.getRecordTime() : System.currentTimeMillis());

        try {
            recordRepository.save(record);
            log.info("Record saved: userId={}, lockId={}, action={}", userId, request.getLockId(), record.getAction());
        } catch (Exception e) {
            log.warn("Record save error: {}", e.getMessage());
        }
    }

    public Map<String, Object> getRecordList(Long userId, Long lockId, Long keyUserId, String keyboardPwd, Long operatorUid, int pageNo, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNo - 1, pageSize);

        Page<Record> records;
        if (keyboardPwd != null && !keyboardPwd.isEmpty()) {
            records = recordRepository.findByLockIdAndKeyboardPwdOrderByRecordTimeDesc(lockId, keyboardPwd, pageRequest);
        } else if (operatorUid != null) {
            records = recordRepository.findByLockIdAndUidOrderByRecordTimeDesc(lockId, operatorUid, pageRequest);
        } else if (keyUserId != null) {
            records = recordRepository.findByLockIdAndUserIdOrderByRecordTimeDesc(lockId, keyUserId, pageRequest);
        } else {
            Lock lock = lockRepository.findById(lockId).orElse(null);
            if (lock != null && lock.getUserId().equals(userId)) {
                records = recordRepository.findByLockIdOrderByRecordTimeDesc(lockId, pageRequest);
            } else {
                records = recordRepository.findByLockIdAndUserIdOrderByRecordTimeDesc(lockId, userId, pageRequest);
            }
        }

        List<Map<String, Object>> list = records.getContent().stream().map(record -> {
            Map<String, Object> map = new HashMap<>();
            map.put("recordId", record.getId());
            map.put("lockId", record.getLockId());

            String action = record.getAction();
            String actionName;
            Integer recordType = record.getRecordType();
            if (recordType != null) {
                actionName = switch (recordType) {
                    case 1 -> "蓝牙开锁";
                    case 4 -> "密码开锁成功";
                    case 5 -> "在锁上修改密码";
                    case 6 -> "在锁上删除密码";
                    case 7 -> "密码开锁失败";
                    case 8 -> "清空密码";
                    case 9 -> "密码被挤掉";
                    case 10 -> "带删除功能密码开锁";
                    case 11 -> "密码开锁失败(过期)";
                    case 12 -> "密码开锁失败(容量不足)";
                    case 13 -> "密码开锁失败(黑名单)";
                    case 14 -> "门锁重新上电";
                    case 15 -> "添加IC卡";
                    case 16 -> "清空IC卡";
                    case 17 -> "IC卡开门成功";
                    case 18 -> "删除IC卡";
                    case 19 -> "手环开门成功";
                    case 20 -> "指纹开锁成功";
                    case 21 -> "指纹添加成功";
                    case 22 -> "指纹开门失败(过期)";
                    case 23 -> "删除指纹";
                    case 24 -> "清空指纹";
                    case 25 -> "IC卡开门失败(过期)";
                    case 26 -> "蓝牙闭锁";
                    case 27 -> "机械钥匙开锁";
                    case 28 -> "网关开锁";
                    case 29 -> "非法开锁";
                    case 30 -> "门磁合上(关门)";
                    case 31 -> "门磁打开(开门)";
                    case 32 -> "从内部开门";
                    case 33 -> "指纹关锁";
                    case 34 -> "密码关锁";
                    case 35 -> "IC卡关锁";
                    case 36 -> "机械钥匙关锁";
                    case 37 -> "APP按键控制";
                    case 38 -> "密码开锁失败(反锁)";
                    case 39 -> "IC卡开锁失败(反锁)";
                    case 40 -> "指纹开锁失败(反锁)";
                    case 41 -> "APP开锁失败(反锁)";
                    case 44 -> "防撬报警";
                    case 45 -> "自动闭锁";
                    case 46 -> "开锁按键开锁";
                    case 47 -> "闭锁按键闭锁";
                    case 48 -> "系统被锁定";
                    case 49 -> "酒店卡开锁";
                    case 50 -> "高温开锁";
                    case 51 -> "IC卡开锁失败(黑名单)";
                    case 52 -> "APP锁定锁";
                    case 53 -> "密码锁定锁";
                    case 54 -> "车离开";
                    case 55 -> "遥控开闭锁";
                    case 56 -> "无线键盘电量";
                    case 57 -> "二维码开锁成功";
                    case 58 -> "二维码开锁失败(过期)";
                    case 59 -> "开启反锁";
                    case 60 -> "关闭反锁";
                    case 61 -> "二维码闭锁成功";
                    case 62 -> "二维码开锁失败(反锁)";
                    case 63 -> "常开时间段自动开锁";
                    case 64 -> "门未关报警";
                    case 65 -> "开锁超时";
                    case 66 -> "闭锁超时";
                    case 67 -> "3D人脸开锁成功";
                    case 68 -> "3D人脸开锁失败(反锁)";
                    case 69 -> "3D人脸闭锁";
                    case 70 -> "注册3D人脸成功";
                    case 71 -> "3D人脸开门失败(过期)";
                    case 72 -> "删除人脸成功";
                    case 73 -> "清空人脸成功";
                    case 74 -> "IC卡开锁失败(CPU安全信息错)";
                    case 75 -> "APP授权按键开锁成功";
                    case 76 -> "网关授权按键开锁成功";
                    case 77 -> "双重认证蓝牙开锁验证成功";
                    case 78 -> "双重认证密码开锁验证成功";
                    case 79 -> "双重认证指纹开锁验证成功";
                    case 80 -> "双重认证IC卡开锁验证成功";
                    case 81 -> "双重认证人脸开锁验证成功";
                    case 82 -> "双重认证遥控开锁验证成功";
                    case 83 -> "双重认证掌静脉开锁验证成功";
                    case 84 -> "掌静脉开锁成功";
                    case 85 -> "掌静脉开锁失败(反锁)";
                    case 86 -> "掌静脉闭锁";
                    case 87 -> "注册掌静脉成功";
                    case 88 -> "掌静脉开门失败(过期)";
                    case 89 -> "删除掌静脉成功";
                    case 90 -> "清空掌静脉成功";
                    case 91 -> "IC卡开锁失败";
                    case 92 -> "管理员密码开锁";
                    case 93 -> "添加密码成功";
                    default -> "操作(类型" + recordType + ")";
                };
            } else if ("lock".equals(action)) {
                actionName = "闭锁";
            } else if ("unlock".equals(action)) {
                actionName = "APP开锁";
            } else {
                actionName = "操作";
            }

            map.put("action", action);
            map.put("actionName", actionName);
            map.put("recordType", recordType);
            map.put("result", record.getResult());
            map.put("recordTime", record.getRecordTime());
            map.put("createdAt", record.getCreatedAt());
            map.put("keyboardPwd", record.getKeyboardPwd());
            map.put("passwordName", record.getPasswordName());
            map.put("failReason", record.getFailReason());

            User opUser = userService.getUserById(record.getUserId());
            map.put("userName", opUser != null ? opUser.getUsername() : "");
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("list", list);
        response.put("total", records.getTotalElements());
        response.put("pageNo", pageNo);
        response.put("pageSize", pageSize);

        return response;
    }

    public void syncOperationLog(Long userId, Long localLockId, String logJson) {
        Lock lock = lockRepository.findById(localLockId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        User user = userService.getUserById(userId);

        try {
            Map<String, Object> result = ttLockClient.uploadRecord(
                    user.getTtAccessToken(),
                    lock.getLockId().intValue(),
                    logJson);
            if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
                log.warn("TTLock record upload failed: {}", result.get("errmsg"));
            }
        } catch (Exception e) {
            log.warn("TTLock record upload error: {}", e.getMessage());
        }

        try {
            List<Map<String, Object>> records = objectMapper.readValue(logJson,
                    new TypeReference<List<Map<String, Object>>>() {});

            if (!records.isEmpty()) {
                log.info("======= BLE Operation Log Start =======");
                log.info("Total records received: {}", records.size());
                log.info("First record fields: {}", records.get(0).keySet());
                for (int i = 0; i < records.size(); i++) {
                    log.info("Record[{}]: {}", i, records.get(i));
                }
                log.info("======= BLE Operation Log End =========");

                Object eq = records.get(0).get("electricQuantity");
                if (eq != null) {
                    lock.setElectricQuantity(((Number) eq).intValue());
                    lockRepository.save(lock);
                    log.info("Updated lock electricQuantity to {} for lockId={}", eq, localLockId);
                }
            }

            for (Map<String, Object> rec : records) {
                Object recordTypeObj = rec.get("recordType");
                String action = "unlock";
                Integer recordType = null;
                if (recordTypeObj != null) {
                    recordType = ((Number) recordTypeObj).intValue();
                    if (recordType == 26 || recordType == 33 || recordType == 34 || recordType == 35
                            || recordType == 36 || recordType == 37 || recordType == 45
                            || recordType == 47 || recordType == 61 || recordType == 66
                            || recordType == 69 || recordType == 86) {
                        action = "lock";
                    }
                }

                Long recordTime = null;
                Object operateDate = rec.get("operateDate");
                if (operateDate != null) {
                    recordTime = ((Number) operateDate).longValue();
                }
                if (recordTime == null) {
                    recordTime = System.currentTimeMillis();
                }

                // Extract password from BLE data
                String pwd = null;
                Object pwdObj = rec.get("password");
                if (pwdObj != null) {
                    pwd = pwdObj.toString();
                }

                // Extract uid from BLE data (user who performed the operation)
                Long uid = null;
                Object uidObj = rec.get("uid");
                if (uidObj != null) {
                    uid = ((Number) uidObj).longValue();
                }

                // Check if record already exists (unique constraint: lock_id, action, record_time)
                var existingOpt = recordRepository.findByLockIdAndActionAndRecordTime(localLockId, action, recordTime);

                if (existingOpt.isPresent()) {
                    Record existing = existingOpt.get();
                    boolean updated = false;
                    if (existing.getKeyboardPwd() == null && pwd != null) {
                        existing.setKeyboardPwd(pwd);
                        updated = true;
                    }
                    if (existing.getPasswordName() == null && pwd != null && pwd.length() >= 4) {
                        var pcOpt = passcodeRepository.findByLockIdAndKeyboardPwd(localLockId, pwd);
                        if (pcOpt.isPresent()) {
                            existing.setPasswordName(pcOpt.get().getKeyboardPwdName());
                            updated = true;
                        }
                    }
                    if (existing.getUid() == null && uid != null) {
                        existing.setUid(uid);
                        updated = true;
                    }
                    if (updated) {
                        recordRepository.save(existing);
                        log.info("Updated existing record id={} with new info", existing.getId());
                    }
                    continue;
                }

                // Insert new record
                Record record = new Record();
                record.setUserId(userId);
                record.setLockId(localLockId);
                record.setAction(action);
                record.setRecordType(recordType);
                record.setResult(recordType != null && isFailedType(recordType) ? "fail" : "success");
                record.setRecordTime(recordTime);
                record.setUid(uid);

                if (pwd != null) {
                    record.setKeyboardPwd(pwd);
                    if (pwd.length() >= 4) {
                        passcodeRepository.findByLockIdAndKeyboardPwd(localLockId, pwd)
                            .ifPresent(pc -> record.setPasswordName(pc.getKeyboardPwdName()));
                    }
                }

                try {
                    recordRepository.save(record);
                } catch (Exception e) {
                    log.warn("Skip duplicate record: {}", e.getMessage());
                }
            }
            log.info("Synced {} records for lockId={}", records.size(), localLockId);
        } catch (Exception e) {
            log.warn("Failed to parse operation log: {}", e.getMessage());
        }
    }

    private boolean isFailedType(int recordType) {
        return switch (recordType) {
            case 7, 11, 12, 13, 22, 25, 38, 39, 40, 41, 48, 51,
                 58, 62, 64, 65, 66, 68, 71, 85, 88, 91 -> true;
            default -> false;
        };
    }
}
