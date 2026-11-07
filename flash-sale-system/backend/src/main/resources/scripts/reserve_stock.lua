
local saleKey = KEYS[1]
local quantity = tonumber(ARGV[1])
local now = tonumber(ARGV[2])

local metadata = redis.call('HMGET', saleKey .. ':metadata', 'status', 'startTime', 'endTime')
local status = metadata[1]
local startTime = tonumber(metadata[2])
local endTime = tonumber(metadata[3])

if status ~= 'ACTIVE' then
    return -1 -- SALE_NOT_ACTIVE
end

if not startTime or not endTime or now < startTime or now > endTime then
    return -2 -- SALE_OUT_OF_WINDOW
end

local currentStock = tonumber(redis.call('GET', saleKey .. ':stock'))
if not currentStock then
    return -3 -- STOCK_NOT_INITIALIZED
end

if currentStock < quantity then
    return -4 -- INSUFFICIENT_STOCK
end

local newStock = redis.call('DECRBY', saleKey .. ':stock', quantity)
return newStock
