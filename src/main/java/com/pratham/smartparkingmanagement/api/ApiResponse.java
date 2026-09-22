package com.pratham.smartparkingmanagement.api;

public class ApiResponse {

    private boolean success;
    private Object data;
    private String error;

    public ApiResponse(boolean success, Object data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static ApiResponse success(Object data) {
        return new ApiResponse(true, data, null);
    }

    public static ApiResponse failure(String error) {
        return new ApiResponse(false, null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getError() {
        return error;
    }
}