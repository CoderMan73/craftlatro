-- nativefs.lua - Fake nativefs module for love.js compatibility
-- Wraps love.filesystem to provide nativefs API
-- Source: https://github.com/W0W53R/web-balatro

local nativefs = {}

function join_path(a, b)
    if b:find("^/") then
        return b
    end
    if not a:find("/$") then
        a = a .. "/"
    end
    return a .. b
end

nativefs.workingDirectory = ""

function nativefs.read(filename)
    if love.filesystem.getInfo(join_path(nativefs.workingDirectory, filename)) then
        return love.filesystem.read(join_path(nativefs.workingDirectory, filename))
    else
        return nil, "File does not exist"
    end
end

function nativefs.write(filename, contents)
    return love.filesystem.write(join_path(nativefs.workingDirectory, filename), contents)
end

function nativefs.getInfo(path)
    return love.filesystem.getInfo(join_path(nativefs.workingDirectory, path))
end

function nativefs.getDirectoryItemsInfo(path)
    local files = love.filesystem.getDirectoryItems(join_path(nativefs.workingDirectory, path))
    local out = {}
    for i, v in ipairs(files) do
        local info = love.filesystem.getInfo(join_path(join_path(nativefs.workingDirectory, path), v))
        out[i] = { name = v, type = info.type }
    end
    return out
end

function nativefs.exists(path)
    return love.filesystem.getInfo(join_path(nativefs.workingDirectory, path)) ~= nil
end

function nativefs.getDirectoryItems(path)
    return love.filesystem.getDirectoryItems(join_path(nativefs.workingDirectory, path))
end

function nativefs.mkdir(path)
    return love.filesystem.createDirectory(join_path(nativefs.workingDirectory, path))
end

function nativefs.remove(path)
    return love.filesystem.remove(join_path(nativefs.workingDirectory, path))
end

function nativefs.load(filename)
    local contents, err = nativefs.read(filename)
    if not contents then return nil, err end
    return load(contents, '@' .. filename)
end

function nativefs.newFileData(path)
    return love.filesystem.newFileData(join_path(nativefs.workingDirectory, path))
end

function nativefs.setWorkingDirectory(path)
    nativefs.workingDirectory = join_path(nativefs.workingDirectory, path)
end

function nativefs.getWorkingDirectory()
    return nativefs.workingDirectory
end

function nativefs.lines(filename)
    local content = nativefs.read(filename)
    local i = 1
    return function()
        if not content then return nil end
        local next_newline = content:find("\n", i)
        if not next_newline then
            local line = content:sub(i)
            content = nil
            return line
        else
            local line = content:sub(i, next_newline - 1)
            i = next_newline + 1
            return line
        end
    end
end

return nativefs
