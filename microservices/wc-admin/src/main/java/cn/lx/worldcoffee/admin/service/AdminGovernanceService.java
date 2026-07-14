package cn.lx.worldcoffee.admin.service;

import cn.lx.worldcoffee.admin.dao.AdminOperationLogDao;
import cn.lx.worldcoffee.admin.dao.SensitiveWordDao;
import cn.lx.worldcoffee.admin.domain.AdminOperationLog;
import cn.lx.worldcoffee.admin.domain.SensitiveWord;
import cn.lx.worldcoffee.admin.domain.from.SensitiveWordForm;
import cn.lx.worldcoffee.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminGovernanceService {

    private final SensitiveWordDao sensitiveWordDao;
    private final AdminOperationLogDao operationLogDao;

    public List<SensitiveWord> listSensitiveWords(String keyword, Integer status) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SensitiveWord::getWord, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(SensitiveWord::getStatus, status);
        }
        wrapper.orderByDesc(SensitiveWord::getUpdateTime).orderByDesc(SensitiveWord::getId);
        return sensitiveWordDao.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public SensitiveWord createSensitiveWord(SensitiveWordForm form) {
        SensitiveWord item = new SensitiveWord();
        item.setWord(normalizeWord(form.getWord()));
        item.setCategory(normalizeCategory(form.getCategory()));
        item.setAction(normalizeAction(form.getAction()));
        item.setStatus(1);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        sensitiveWordDao.insert(item);
        log("governance", "create_sensitive_word", "sensitive_word", item.getId(), item.getWord());
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public SensitiveWord updateSensitiveWord(Long id, SensitiveWordForm form) {
        SensitiveWord item = requireSensitiveWord(id);
        item.setWord(normalizeWord(form.getWord()));
        item.setCategory(normalizeCategory(form.getCategory()));
        item.setAction(normalizeAction(form.getAction()));
        item.setUpdateTime(LocalDateTime.now());
        sensitiveWordDao.updateById(item);
        log("governance", "update_sensitive_word", "sensitive_word", id, item.getWord());
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleSensitiveWord(Long id) {
        SensitiveWord item = requireSensitiveWord(id);
        item.setStatus(item.getStatus() == null || item.getStatus() == 0 ? 1 : 0);
        item.setUpdateTime(LocalDateTime.now());
        sensitiveWordDao.updateById(item);
        log("governance", "toggle_sensitive_word", "sensitive_word", id, String.valueOf(item.getStatus()));
    }

    public List<AdminOperationLog> listOperationLogs(String module, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) {
            wrapper.eq(AdminOperationLog::getModule, module.trim());
        }
        wrapper.orderByDesc(AdminOperationLog::getCreateTime)
                .last("LIMIT " + (safePage - 1) * safeSize + "," + safeSize);
        return operationLogDao.selectList(wrapper);
    }

    public void log(String module, String action, String targetType, Long targetId, String detail) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(0L);
        log.setAdminName("admin");
        log.setModule(module);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        operationLogDao.insert(log);
    }

    private SensitiveWord requireSensitiveWord(Long id) {
        SensitiveWord item = sensitiveWordDao.selectById(id);
        if (item == null) throw new ServiceException("敏感词不存在");
        return item;
    }

    private String normalizeWord(String word) {
        String normalized = word == null ? "" : word.trim();
        if (normalized.isBlank()) throw new ServiceException("敏感词不能为空");
        return normalized;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "general";
        String normalized = category.trim();
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private Integer normalizeAction(Integer action) {
        return action == null || action < 1 || action > 2 ? 1 : action;
    }
}
