local cloneref = (cloneref or clonereference or function(instance: any) return instance end)
local httpService = cloneref(game:GetService('HttpService'))
local isfolder, isfile, listfiles = isfolder, isfile, listfiles

if typeof(copyfunction) == "function" then
	local isfolder_copy, isfile_copy, listfiles_copy = copyfunction(isfolder), copyfunction(isfile), copyfunction(listfiles)
	local isfolder_success, isfolder_error = pcall(function()
		return isfolder_copy("test" .. tostring(math.random(1000000, 9999999)))
	end)
	if isfolder_success == false or typeof(isfolder_error) ~= "boolean" then
		isfolder = function(folder)
			local success, data = pcall(isfolder_copy, folder)
			return (if success then data else false)
		end
		isfile = function(file)
			local success, data = pcall(isfile_copy, file)
			return (if success then data else false)
		end
		listfiles = function(folder)
			local success, data = pcall(listfiles_copy, folder)
			return (if success then data else {})
		end
	end
end

if not Callisto then
	Callisto = {}
end
if not Callisto.Flags then
	Callisto.Flags = {}
end

local SaveManager = {} do
	-- ─────────────────────────────────────────────────────────────
	-- BRANDING / ISOLATION
	-- Folder = top-level root (shared name across your scripts is fine)
	-- Namespace = per-script isolation. Change this in EACH script so
	--             two different scripts never share saves / autoload.
	-- ─────────────────────────────────────────────────────────────
	SaveManager.Folder     = 'DuckySettings'
	SaveManager.Namespace  = 'ducky'   -- <-- override per script if needed
	SaveManager.SubFolder  = ''        -- legacy compat (unused when Namespace set)
	SaveManager.Ignore     = {}
	SaveManager.Library    = nil
	SaveManager.Elements   = {}
	SaveManager.AutoLoadStatusLabel = nil
	SaveManager.ConfigListDropdown  = nil
	SaveManager._lastList = ""

	------------------------------------------------------------------
	-- Serialization (Color3 / Enum can't go through JSONEncode)
	------------------------------------------------------------------
	local function encodeValue(v)
		local t = typeof(v)
		if t == "boolean" or t == "number" or t == "string" then
			return v
		elseif t == "Color3" then
			return { __t = "Color3", r = v.R, g = v.G, b = v.B }
		elseif t == "EnumItem" then
			return { __t = "Enum", e = tostring(v.EnumType), n = v.Name }
		elseif t == "table" then
			local out = {}
			for k, val in pairs(v) do
				local ev = encodeValue(val)
				if ev ~= nil then
					out[typeof(k) == "number" and k or tostring(k)] = ev
				end
			end
			return out
		end
		return nil
	end

	local function decodeValue(v)
		if type(v) == "table" then
			if v.__t == "Color3" then
				return Color3.new(v.r, v.g, v.b)
			elseif v.__t == "Enum" then
				local ok, item = pcall(function() return Enum[v.e][v.n] end)
				return ok and item or nil
			end
			local out = {}
			for k, val in pairs(v) do
				out[k] = decodeValue(val)
			end
			return out
		end
		return v
	end

	------------------------------------------------------------------
	-- Namespace / path helpers (isolation lives here)
	------------------------------------------------------------------
	function SaveManager:SetNamespace(name)
		if type(name) ~= "string" or name:gsub("%s", "") == "" then return end
		-- sanitize so it can't escape the folder
		name = name:gsub("[/\\]", "_")
		self.Namespace = name
		self:BuildFolderTree()
	end

	-- The per-script leaf folder. If Namespace is empty, we fall back
	-- to the legacy SubFolder behavior to stay backwards-compatible.
	function SaveManager:_leafFolder()
		if self.Namespace and self.Namespace ~= "" then
			return self.Namespace
		end
		return self.SubFolder
	end

	function SaveManager:CheckSubFolder(createFolder)
		local leaf = self:_leafFolder()
		if typeof(leaf) ~= "string" or leaf == "" then return false end
		if createFolder == true then
			local path = self.Folder .. "/settings/" .. leaf
			if not isfolder(path) then
				makefolder(path)
			end
		end
		return true
	end

	function SaveManager:GetPaths()
		local paths = {}
		local parts = self.Folder:split('/')
		for idx = 1, #parts do
			local path = table.concat(parts, '/', 1, idx)
			if not table.find(paths, path) then paths[#paths + 1] = path end
		end
		paths[#paths + 1] = self.Folder .. '/themes'
		paths[#paths + 1] = self.Folder .. '/settings'
		if self:CheckSubFolder(false) then
			local subFolder = self.Folder .. "/settings/" .. self:_leafFolder()
			parts = subFolder:split('/')
			for idx = 1, #parts do
				local path = table.concat(parts, '/', 1, idx)
				if not table.find(paths, path) then paths[#paths + 1] = path end
			end
		end
		return paths
	end

	function SaveManager:BuildFolderTree()
		local paths = self:GetPaths()
		for i = 1, #paths do
			local str = paths[i]
			if isfolder(str) then continue end
			makefolder(str)
		end
	end

	function SaveManager:CheckFolderTree()
		if isfolder(self.Folder) then return end
		self:BuildFolderTree()
		task.wait(0.1)
	end

	function SaveManager:SetIgnoreIndexes(list)
		for _, key in next, list do
			self.Ignore[key] = true
		end
	end

	function SaveManager:SetFolder(folder)
		self.Folder = folder
		self:BuildFolderTree()
	end

	-- Kept for backwards compat; now maps to Namespace.
	function SaveManager:SetSubFolder(folder)
		self:SetNamespace(folder)
	end

	function SaveManager:_settingsPath(name)
		self:CheckSubFolder(true)
		if self:_leafFolder() ~= "" then
			return self.Folder .. "/settings/" .. self:_leafFolder() .. "/" .. name .. '.json'
		end
		return self.Folder .. '/settings/' .. name .. '.json'
	end

	function SaveManager:_autoloadPath()
		self:CheckSubFolder(true)
		if self:_leafFolder() ~= "" then
			return self.Folder .. "/settings/" .. self:_leafFolder() .. "/autoload.txt"
		end
		return self.Folder .. "/settings/autoload.txt"
	end

	------------------------------------------------------------------
	-- Element registration
	------------------------------------------------------------------
	function SaveManager:RegisterElement(flag, element, type)
		if not flag or not element then return end
		self.Elements[flag] = { Element = element, Type = type or "Unknown" }
	end

	local HOOK_METHODS = {
		AddToggle = "Toggles",
		AddCheckbox = "Checkboxes",
		AddSlider = "Sliders",
		AddDropdown = "Dropdowns",
		AddColorpicker = "Colorpickers",
		AddColorPicker = "Colorpickers",
		AddKeybind = "Keybinds",
		AddInput = "Inputs",
		AddTextbox = "Inputs",
	}

	function SaveManager:Hook(target)
		if self.Library and self.Library.AutoRegister then return target end
		if type(target) ~= "table" and typeof(target) ~= "table" then return target end
		if rawget(target, "__SaveManagerHooked") then return target end
		pcall(function() target.__SaveManagerHooked = true end)

		local manager = self
		for methodName, kind in pairs(HOOK_METHODS) do
			local original = target[methodName]
			if type(original) == "function" then
				target[methodName] = function(selfArg, config, ...)
					local flag = type(config) == "table" and config.Flag or nil

					if flag then
						local origCallback = config.Callback
						config.Callback = function(...)
							local value = ...
							if manager.Library then
								manager.Library.Flags = manager.Library.Flags or {}
								manager.Library.Flags[flag] = value
							end
							if origCallback then
								return origCallback(...)
							end
						end
					end

					local element = original(selfArg, config, ...)

					if flag and type(element) == "table" then
						local origSet = element.Set
						if type(origSet) == "function" then
							pcall(function()
								element.Set = function(e, value, ...)
									local results = table.pack(origSet(e, value, ...))
									if manager.Library then
										manager.Library.Flags = manager.Library.Flags or {}
										manager.Library.Flags[flag] = value
									end
									return table.unpack(results, 1, results.n)
								end
							end)
						end
						manager:RegisterElement(flag, element, kind)

						if manager.Library then
							manager.Library.Flags = manager.Library.Flags or {}
							if manager.Library.Flags[flag] == nil and config.Default ~= nil then
								manager.Library.Flags[flag] = config.Default
							end
						end
					end

					return element
				end
			end
		end
		return target
	end

	------------------------------------------------------------------
	-- Apply flags -> UI
	------------------------------------------------------------------
	local function setElement(entry, value)
		local element, kind = entry.Element, entry.Type
		if kind == "Colorpickers" and type(value) == "string" then
			local ok, c = pcall(Color3.fromHex, value)
			if ok then value = c end
		end
		if kind == "Keybinds" then
			if type(value) == "string" and value ~= "None" then
				local ok, k = pcall(function() return Enum.KeyCode[value] end)
				if ok then value = k end
			elseif type(value) == "number" then
				local ok, k = pcall(function() return Enum.KeyCode:FromValue(value) end)
				if ok and k then value = k end
			end
		end

		local ok = false
		if type(element.Set) == "function" then
			ok = pcall(element.Set, element, value)
		end
		if not ok and type(element.SetValue) == "function" then
			ok = pcall(element.SetValue, element, value)
		end
		return ok
	end

	function SaveManager:ApplyFlagsToUI(values)
		local flags = self.Library and self.Library.Flags
		if not flags then return end
		values = values or flags

		for flag, value in pairs(values) do
			if self.Ignore[flag] then continue end
			local entry = self.Elements[flag] or (self.Library.Registry and self.Library.Registry[flag])
			if entry then
				if type(value) == "boolean" then
					flags[flag] = not value
				else
					flags[flag] = nil
				end
				setElement(entry, value)
				flags[flag] = value
			else
				flags[flag] = value
			end
		end
	end

	------------------------------------------------------------------
	-- Misc helpers
	------------------------------------------------------------------
	function SaveManager:UpdateConfigList(force)
		local configs = self:RefreshConfigList()
		local key = table.concat(configs, "|")
		if self.ConfigListDropdown and (force or key ~= self._lastList) then
			self._lastList = key
			pcall(function() self.ConfigListDropdown:Refresh(configs) end)
		end
		return configs
	end

	-- Accent color as a hex string so status text can be tinted via RichText.
	local function accentHex(): string
		local color = (Callisto.Theme and Callisto.Theme.Accent) or Color3.fromRGB(0, 160, 255)
		return string.format("#%02X%02X%02X",
			math.floor(color.R * 255 + 0.5),
			math.floor(color.G * 255 + 0.5),
			math.floor(color.B * 255 + 0.5))
	end

	function SaveManager:_statusText(): string
		local current = self:GetAutoloadConfig()
		local shown = current == "none"
			and '<font transparency="0.5">None</font>'
			or ('<font color="' .. accentHex() .. '">' .. current .. '</font>')
		return "Autoload:  " .. shown
	end

	function SaveManager:UpdateAutoLoadStatus()
		if self.AutoLoadStatusLabel then
			pcall(function() self.AutoLoadStatusLabel:Set(self:_statusText()) end)
		end
	end

	function SaveManager:SetLibrary(library)
		self.Library = library
		if self.Library and not self.Library.Flags then
			self.Library.Flags = {}
		end
	end

	function SaveManager:IgnoreThemeSettings()
		self:SetIgnoreIndexes({
			"Background", "Foreground", "ForegroundLight", "Border", "Accent", "AccentLight", "AccentDark", "Text",
			"SaveManager_ConfigList", "SaveManager_ConfigName",
			"ThemeManager_ThemeList"
		})
	end

	------------------------------------------------------------------
	-- Save / Load / Delete
	------------------------------------------------------------------
	function SaveManager:Save(name)
		if (not name) or name:gsub(' ', '') == '' then
			return false, 'no config file is selected'
		end
		self:CheckFolderTree()

		local data = { flags = {}, namespace = self:_leafFolder() }
		local flags = self.Library and self.Library.Flags
		if flags then
			for idx, value in pairs(flags) do
				if self.Ignore[idx] then continue end
				local encoded = encodeValue(value)
				if encoded ~= nil then
					data.flags[idx] = encoded
				end
			end
		end

		local success, encoded = pcall(httpService.JSONEncode, httpService, data)
		if not success then
			return false, 'failed to encode data'
		end

		local okWrite = pcall(writefile, self:_settingsPath(name), encoded)
		if not okWrite then return false, 'write file error' end
		return true
	end

	function SaveManager:Load(name)
		if (not name) then
			return false, 'no config file is selected'
		end
		self:CheckFolderTree()

		local file = self:_settingsPath(name)
		if not isfile(file) then return false, 'invalid file' end

		local success, decoded = pcall(function()
			return httpService:JSONDecode(readfile(file))
		end)
		if not success or type(decoded) ~= "table" then return false, 'decode error' end

		-- Guard: refuse to load a config saved under a different namespace
		-- if the file happens to contain one. Prevents cross-script bleed.
		if decoded.namespace ~= nil and self:_leafFolder() ~= "" and decoded.namespace ~= self:_leafFolder() then
			return false, 'namespace mismatch (config belongs to another script)'
		end

		if type(decoded.flags) == "table" and self.Library then
			self.Library.Flags = self.Library.Flags or {}
			local values = {}
			for idx, value in pairs(decoded.flags) do
				if not self.Ignore[idx] then
					values[idx] = decodeValue(value)
				end
			end
			self:ApplyFlagsToUI(values)
		end

		return true
	end

	function SaveManager:Delete(name)
		if (not name) then
			return false, 'no config file is selected'
		end

		local file = self:_settingsPath(name)
		if not isfile(file) then return false, 'invalid file' end

		local success = pcall(delfile, file)
		if not success then return false, 'delete file error' end

		return true
	end

	function SaveManager:RefreshConfigList()
		local success, data = pcall(function()
			self:CheckFolderTree()
			local list
			if self:_leafFolder() ~= "" then
				list = listfiles(self.Folder .. "/settings/" .. self:_leafFolder())
			else
				list = listfiles(self.Folder .. "/settings")
			end
			if typeof(list) ~= "table" then list = {} end

			local out = {}
			for i = 1, #list do
				local name = tostring(list[i]):match("([^/\\]+)%.json$")
				if name then
					table.insert(out, name)
				end
			end
			table.sort(out)
			return out
		end)

		if (not success) then
			return {}
		end
		return data
	end

	------------------------------------------------------------------
	-- Autoload
	------------------------------------------------------------------
	function SaveManager:GetAutoloadConfig()
		self:CheckFolderTree()
		local path = self:_autoloadPath()
		if isfile(path) then
			local successRead, name = pcall(readfile, path)
			if not successRead then
				return "none"
			end
			name = tostring(name)
			return if name == "" then "none" else name
		end
		return "none"
	end

	function SaveManager:LoadAutoloadConfig()
		local name = self:GetAutoloadConfig()
		if name == "none" then return end
		self:Load(name)
		self:UpdateAutoLoadStatus()
	end

	function SaveManager:SaveAutoloadConfig(name)
		self:CheckFolderTree()
		local success = pcall(writefile, self:_autoloadPath(), name)
		if not success then return false, 'write file error' end
		self:UpdateAutoLoadStatus()
		return true, ""
	end

	function SaveManager:DeleteAutoLoadConfig()
		self:CheckFolderTree()
		local path = self:_autoloadPath()
		if isfile(path) then
			local success = pcall(delfile, path)
			if not success then return false, 'delete file error' end
		end
		self:UpdateAutoLoadStatus()
		return true, ""
	end

	------------------------------------------------------------------
	-- UI
	-- Layout: name input -> create, config picker, then paired buttons
	-- (two per row so every label fits), then a colored autoload status.
	------------------------------------------------------------------
	function SaveManager:BuildConfigSection(section)
		assert(self.Library, 'Must set SaveManager.Library')

		if not self.Library.Flags then
			self.Library.Flags = {}
		end

		self:SetIgnoreIndexes({ 'SaveManager_ConfigList', 'SaveManager_ConfigName' })

		local configSection = section

		local function selected()
			local name = self.Library.Flags.SaveManager_ConfigList
			if type(name) ~= "string" or name == "" then return nil end
			return name
		end

		-- 1. New config
		configSection:AddInput({
			Title = "New Config",
			Placeholder = "Type a name...",
			Default = "",
			Flag = "SaveManager_ConfigName",
		})

		configSection:AddButton({
			Title = "Create Config",
			Callback = function()
				local name = tostring(self.Library.Flags.SaveManager_ConfigName or "")
				if name:gsub(' ', '') == '' then return end
				if self:Save(name) then
					self:UpdateConfigList(true)
					self:UpdateAutoLoadStatus()
				end
			end
		})

		-- 2. Saved configs
		self.ConfigListDropdown = configSection:AddDropdown({
			Title = "Saved Configs",
			Placeholder = "Select a config...",
			Values = self:RefreshConfigList(),
			Flag = "SaveManager_ConfigList",
			Multi = false,
		})

		configSection:AddButton({
			Buttons = {
				{
					Title = "Load",
					Callback = function()
						local name = selected()
						if not name then return end
						self:Load(name)
						self:UpdateAutoLoadStatus()
					end,
				},
				{
					Title = "Overwrite",
					Callback = function()
						local name = selected()
						if not name then return end
						self:Save(name)
					end,
				},
			},
		})

		configSection:AddButton({
			Buttons = {
				{
					Title = "Delete",
					Callback = function()
						local name = selected()
						if not name then return end
						self:Delete(name)
						self.Library.Flags.SaveManager_ConfigList = nil
						self:UpdateConfigList(true)
						self:UpdateAutoLoadStatus()
					end,
				},
				{
					Title = "Refresh",
					Callback = function()
						self:UpdateConfigList(true)
					end,
				},
			},
		})

		-- 3. Autoload
		configSection:AddButton({
			Buttons = {
				{
					Title = "Set Autoload",
					Callback = function()
						local name = selected()
						if not name then return end
						self:SaveAutoloadConfig(name)
					end,
				},
				{
					Title = "Clear Autoload",
					Callback = function()
						self:DeleteAutoLoadConfig()
					end,
				},
			},
		})

		self.AutoLoadStatusLabel = configSection:AddLabel({
			Title = self:_statusText()
		})
	end

	SaveManager:BuildFolderTree()
end

return SaveManager
