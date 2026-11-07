
local saleKey = KEYS[1]
local quantity = tonumber(ARGV[1])
local releaseKey = KEYS[2]

local isReleased = redis.call('GET', releaseKey)
if isReleased then
    return -1 -- ALREADY_RELEASED
end

redis.call('SETEX', releaseKey, 86400, "1")
local newStock = redis.call('INCRBY', saleKey .. ':stock', quantity)
return newStock
