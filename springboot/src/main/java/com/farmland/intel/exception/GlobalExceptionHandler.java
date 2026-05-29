package com.farmland.intel.exception;

import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 * 统一处理系统中的各种异常，提供友好的错误提示
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     * @param se 业务异常
     * @return Result
     */
    @ExceptionHandler(ServiceException.class)
    @ResponseBody
    public Result handleServiceException(ServiceException se) {
        log.warn("业务异常：{}", se.getMessage());
        return Result.error(se.getCode(), se.getMessage());
    }

    /**
     * 处理参数验证异常
     * @param e 参数验证异常
     * @return Result
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseBody
    public Result handleValidationException(Exception e) {
        log.warn("参数验证失败：{}", e.getMessage());
        return Result.error("400", "参数格式不正确，请检查输入");
    }

    /**
     * 处理数据库访问异常
     * @param e 数据库异常
     * @return Result
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseBody
    public Result handleDataAccessException(DataAccessException e) {
        log.error("数据库访问异常：", e);
        return Result.error("500", "数据操作失败，请稍后重试");
    }

    /**
     * 处理文件上传大小超限异常
     * @param e 文件上传异常
     * @return Result
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public Result handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传大小超限：{}", e.getMessage());
        return Result.error("413", "文件大小超过限制，请选择较小的文件");
    }

    /**
     * 处理空指针异常
     * @param e 空指针异常
     * @return Result
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseBody
    public Result handleNullPointerException(NullPointerException e) {
        log.error("空指针异常：", e);
        return Result.error("500", "系统内部错误，请联系管理员");
    }

    /**
     * 处理非法参数异常
     * @param e 非法参数异常
     * @return Result
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public Result handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数：{}", e.getMessage());
        return Result.error("400", "参数错误：" + e.getMessage());
    }

    /**
     * JSON 请求体无法反序列化（缺字段、类型不对等）。单独处理避免落入通用 Exception 且便于前端走统一 Result。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public Result handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(Constants.CODE_400, "请求体格式不正确，请检查 JSON 字段");
    }

    /**
     * 处理其他未捕获的异常
     * @param e 通用异常
     * @return Result
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.error("500", "系统繁忙，请稍后重试");
    }
}
