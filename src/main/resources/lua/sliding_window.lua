local time_arr = redis.call('TIME')
local now_sec = tonumber(time_arr[1])
local now_us = tonumber(time_arr[2])
local now_ms = now_sec * 1000 + math.floor(now_us / 1000)
local window_size_ms = tonumber(ARGV[2]) * 1000

local key = KEYS[1]
local tokens = tonumber(ARGV[1])
local max_requests = tonumber(ARGV[3])

local window_start = now_ms - window_size_ms

redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

local current_count = redis.call('ZCARD', key)

if current_count + tokens <= max_requests then
    local base_member = now_ms .. ':' .. now_us
    for i = 1, tokens do
        redis.call('ZADD', key, now_ms, base_member .. ':' .. i)
    end
    return {1, current_count + tokens, 0}
else
    local oldest_arr = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local oldest_ms = 0
    if oldest_arr[1] ~= nil then
        oldest_ms = tonumber(oldest_arr[2])
    end
    return {0, current_count, oldest_ms}
end
