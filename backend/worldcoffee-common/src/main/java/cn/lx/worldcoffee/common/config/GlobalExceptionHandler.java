package cn.lx.worldcoffee.common.config;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Result;
import lombok.AllArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@AllArgsConstructor
public class GlobalExceptionHandler {


   
    /**
     * 处理业务层异常（自定义业务异常）
     */
    @ExceptionHandler(ServiceException.class)
    public Result<?> handleServiceException(ServiceException e) {
        return Result.fail(e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(message);
    }

    /**
     * 处理框架级异常：直接返回 ResponseEntity，避免内容协商问题
     */
    @ExceptionHandler({HttpMediaTypeNotAcceptableException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class,
            NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleFrameworkException(Exception e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 500);
        body.put("msg", e.getMessage());
        body.put("data", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 兜底处理：仅处理非框架级的 RuntimeException
     */
    /**
     * 兜底处理：仅处理 ServiceException 未覆盖的 RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleGenericException(RuntimeException e) {
        return Result.fail(e.getMessage());
    }
}
