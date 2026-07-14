package cn.lx.worldcoffee.admin.controller;

import cn.lx.worldcoffee.admin.domain.AdminOperationLog;
import cn.lx.worldcoffee.admin.domain.SensitiveWord;
import cn.lx.worldcoffee.admin.domain.from.SensitiveWordForm;
import cn.lx.worldcoffee.admin.service.AdminGovernanceService;
import cn.lx.worldcoffee.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/governance")
@RequiredArgsConstructor
public class AdminGovernanceController {

    private final AdminGovernanceService governanceService;

    @GetMapping("/sensitive-words")
    public Result<List<SensitiveWord>> listSensitiveWords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(governanceService.listSensitiveWords(keyword, status));
    }

    @PostMapping("/sensitive-words")
    public Result<SensitiveWord> createSensitiveWord(@Valid @RequestBody SensitiveWordForm form) {
        return Result.success(governanceService.createSensitiveWord(form));
    }

    @PutMapping("/sensitive-words/{id}")
    public Result<SensitiveWord> updateSensitiveWord(
            @PathVariable Long id,
            @Valid @RequestBody SensitiveWordForm form) {
        return Result.success(governanceService.updateSensitiveWord(id, form));
    }

    @PostMapping("/sensitive-words/{id}/toggle")
    public Result<Void> toggleSensitiveWord(@PathVariable Long id) {
        governanceService.toggleSensitiveWord(id);
        return Result.success(null);
    }

    @GetMapping("/operation-logs")
    public Result<List<AdminOperationLog>> listOperationLogs(
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(governanceService.listOperationLogs(module, page, size));
    }
}
