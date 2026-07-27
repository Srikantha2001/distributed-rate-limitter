local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000000 + tonumber(time[2])

local key = KEYS[1]
local tokens = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local refillRate = tonumber(ARGV[3])

local remaining = redis.call('HGET', key, 'remaining')
local lastRefill = redis.call('HGET', key, 'last_refill')

if remaining == false then
    remaining = capacity
    lastRefill = now
else
    remaining = tonumber(remaining)
    lastRefill = tonumber(lastRefill)
end

local elapsed = now - lastRefill
if elapsed > 0 then
    local refillAmount = (elapsed / 1000000.0) * refillRate
    remaining = math.min(capacity, remaining + refillAmount)
end

local allowed = 0
if remaining >= tokens then
    remaining = remaining - tokens
    allowed = 1
end

redis.call('HSET', key, 'remaining', remaining, 'last_refill', now)

return {allowed, remaining}
