-- web_patches.lua - Compatibility patches for running Balatro in love.js
-- Disable steam integration, fix threads, disable mipmaps, patch format/log
-- Source: https://github.com/W0W53R/web-balatro

love.system.getOS = function()
  return "Windows"
end

local _format = string.format
function string:format(key, ...)
    local args = {...}
    local ok, ret = pcall(function() return _format(self, key, unpack(args)) end)
    if ok then
        return ret
    else
        return key
    end
end

function override_setMipmapFilter(texture)
    getmetatable(texture).__index.setMipmapFilter = function() end
    return texture
end

local _newImage = love.graphics.newImage
love.graphics.newImage = function(path, config)
    config = config or {}
    config.mipmaps = false
    return override_setMipmapFilter(_newImage(path, config))
end

local _quit = love.event.quit
love.event.quit = function()
    print("Quitting game...")
    _quit()
end

local _randomSeed = math.randomseed
math.randomseed = function(seed)
    if math.floor(seed) ~= seed then
        _randomSeed((seed % 1) * 2147483647, math.floor(seed))
        return
    end
    _randomSeed(seed)
end

local prevthread = nil

local FakeThread = {}

function FakeThread:new(thread)
    local obj = {}
    obj._thread = thread or coroutine.create(function() end)
    setmetatable(obj, self)
    self.__index = self
    return obj
end

function FakeThread:start(...)
    return coroutine.resume(self._thread, ...)
end

local FakeChannel = {}

function FakeChannel:new(name)
    local obj = {}
    obj.queue = {}
    obj._thread = prevthread and prevthread._thread or nil
    obj.name = name or "unnamed_channel_"..math.random(1,100)
    obj._state = "paused"
    setmetatable(obj, self)
    self.__index = self
    return obj
end

function FakeChannel:push(value)
    if self.name == "save_request" and value == "done" then
        return
    end
    table.insert(self.queue, value)
    if self._thread and coroutine.status(self._thread) == "suspended" then
        coroutine.resume(self._thread)
    end
end

function FakeChannel:pop()
    return table.remove(self.queue, 1)
end

function FakeChannel:demand()
    while #self.queue == 0 do
        coroutine.yield()
    end
    return self:pop()
end

love.thread.newThread = function(path)
    local f = loadstring("local arg = nil\n"..love.filesystem.read(path))
    local thread = coroutine.create(f)
    prevthread = FakeThread:new(thread)
    return prevthread
end

local channels = {}
love.thread.getChannel = function(name)
    if not channels[name] then
        channels[name] = FakeChannel:new(name)
    end
    return channels[name]
end

local _log = math.log
math.log = function(x, base)
  if base then
    return _log(x) / _log(base)
  end
  return _log(x)
end

load = loadstring
