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

local ThemeManager = {} do
	ThemeManager.Folder = 'CallistoThemes'
	ThemeManager.Library = nil
	ThemeManager.DefaultTheme = 'Ducky Main'

	-- Old theme names -> new names, so previously saved defaults still load.
	ThemeManager.Aliases = {
		['runni'] = 'Ducky Main',
		['Default'] = 'Midnight',
		['DarkPurple'] = 'Amethyst',
		['OceanBlue'] = 'Abyss',
		['Crimson'] = 'Blood Moon',
		['Forest'] = 'Emerald',
		['Light'] = 'Daylight',
	}

	-- Shown first in the dropdown, in this order; any others follow alphabetically.
	ThemeManager.ThemeOrder = {
		'Ducky Main', 'Graphite', 'Midnight', 'Amethyst', 'Abyss',
		'Emerald', 'Sunset', 'Blood Moon', 'Daylight',
	}

	ThemeManager.BuiltInThemes = {
		-- Signature theme: warm near-black surfaces with a golden-yellow accent.
		['Ducky Main'] = {
			Background = Color3.fromHex("#000000"),
			Foreground = Color3.fromHex("#000000"),
			ForegroundLight = Color3.fromHex("#000000"),
			Border = Color3.fromHex("#000000"),
			Accent = Color3.fromHex("#f1c40f"),
			AccentLight = Color3.fromHex("#f7d94c"),
			AccentDark = Color3.fromHex("#b8920a"),
			Text = Color3.fromHex("#ffffff"),
		},
		-- Neutral dark with a soft silver accent.
		['Graphite'] = {
			Background = Color3.fromHex("#0f0f10"),
			Foreground = Color3.fromHex("#18181a"),
			ForegroundLight = Color3.fromHex("#212124"),
			Border = Color3.fromHex("#26262a"),
			Accent = Color3.fromHex("#c9ced6"),
			AccentLight = Color3.fromHex("#e6e9ee"),
			AccentDark = Color3.fromHex("#8d939c"),
			Text = Color3.fromHex("#f2f3f5"),
		},
		['Midnight'] = {
			Background = Color3.fromHex("#0b0d14"),
			Foreground = Color3.fromHex("#12151f"),
			ForegroundLight = Color3.fromHex("#1a1e2b"),
			Border = Color3.fromHex("#1e2232"),
			Accent = Color3.fromHex("#4c8dff"),
			AccentLight = Color3.fromHex("#7fb0ff"),
			AccentDark = Color3.fromHex("#2f64c8"),
			Text = Color3.fromHex("#e8eefc"),
		},
		['Amethyst'] = {
			Background = Color3.fromHex("#0f0b16"),
			Foreground = Color3.fromHex("#171221"),
			ForegroundLight = Color3.fromHex("#20192d"),
			Border = Color3.fromHex("#261e36"),
			Accent = Color3.fromHex("#a66bff"),
			AccentLight = Color3.fromHex("#c39bff"),
			AccentDark = Color3.fromHex("#7a42d1"),
			Text = Color3.fromHex("#f1eaff"),
		},
		['Abyss'] = {
			Background = Color3.fromHex("#080f12"),
			Foreground = Color3.fromHex("#0f1a1f"),
			ForegroundLight = Color3.fromHex("#162429"),
			Border = Color3.fromHex("#192a2f"),
			Accent = Color3.fromHex("#1fd1c1"),
			AccentLight = Color3.fromHex("#6ae8dc"),
			AccentDark = Color3.fromHex("#12968a"),
			Text = Color3.fromHex("#e6fbf9"),
		},
		['Emerald'] = {
			Background = Color3.fromHex("#0a110c"),
			Foreground = Color3.fromHex("#101a13"),
			ForegroundLight = Color3.fromHex("#17251b"),
			Border = Color3.fromHex("#1a291e"),
			Accent = Color3.fromHex("#3ddc84"),
			AccentLight = Color3.fromHex("#7aebaa"),
			AccentDark = Color3.fromHex("#26a35f"),
			Text = Color3.fromHex("#e8f9ee"),
		},
		['Sunset'] = {
			Background = Color3.fromHex("#120d0a"),
			Foreground = Color3.fromHex("#1c1511"),
			ForegroundLight = Color3.fromHex("#271c16"),
			Border = Color3.fromHex("#2e221e"),
			Accent = Color3.fromHex("#ff7a2f"),
			AccentLight = Color3.fromHex("#ffa066"),
			AccentDark = Color3.fromHex("#c85a18"),
			Text = Color3.fromHex("#fdf0e6"),
		},
		['Blood Moon'] = {
			Background = Color3.fromHex("#120a0c"),
			Foreground = Color3.fromHex("#1b1013"),
			ForegroundLight = Color3.fromHex("#25161a"),
			Border = Color3.fromHex("#2c1a1e"),
			Accent = Color3.fromHex("#ff4d6a"),
			AccentLight = Color3.fromHex("#ff8097"),
			AccentDark = Color3.fromHex("#c2304a"),
			Text = Color3.fromHex("#fdeaee"),
		},
		['Daylight'] = {
			Background = Color3.fromHex("#f6f6f4"),
			Foreground = Color3.fromHex("#ebebe7"),
			ForegroundLight = Color3.fromHex("#e2e2dd"),
			Border = Color3.fromHex("#ddddd8"),
			Accent = Color3.fromHex("#2f6fed"),
			AccentLight = Color3.fromHex("#6a9bff"),
			AccentDark = Color3.fromHex("#1f4fb3"),
			Text = Color3.fromHex("#15171a"),
		},
	}

	-- Resolves old/saved names to a theme that exists (or nil).
	function ThemeManager:ResolveName(name)
		if type(name) ~= "string" then return nil end
		if self.BuiltInThemes[name] then return name end
		local alias = self.Aliases[name]
		if alias and self.BuiltInThemes[alias] then return alias end
		return nil
	end

	function ThemeManager:SetLibrary(library)
		self.Library = library
		self:ForceTheme()
	end

	function ThemeManager:ForceTheme()
		if not self.Library then return end
		self:ApplyTheme(self.DefaultTheme)
	end

	function ThemeManager:GetPaths()
		local paths = {}
		local parts = self.Folder:split('/')
		for idx = 1, #parts do
			paths[#paths + 1] = table.concat(parts, '/', 1, idx)
		end
		paths[#paths + 1] = self.Folder .. '/themes'
		return paths
	end

	function ThemeManager:BuildFolderTree()
		local paths = self:GetPaths()
		for i = 1, #paths do
			local str = paths[i]
			if isfolder(str) then continue end
			makefolder(str)
		end
	end

	function ThemeManager:CheckFolderTree()
		if isfolder(self.Folder) then return end
		self:BuildFolderTree()
		task.wait(0.1)
	end

	function ThemeManager:SetFolder(folder)
		self.Folder = folder
		self:BuildFolderTree()
	end

	function ThemeManager:ApplyTheme(themeName)
		local resolved = self:ResolveName(themeName)
		if not resolved then return end
		self.Library:SetTheme(self.BuiltInThemes[resolved], true)
		if self.Library.Flags then
			self.Library.Flags.ThemeManager_ThemeList = resolved
		end
	end

	function ThemeManager:SaveDefault(theme)
		writefile(self.Folder .. '/themes/default.txt', theme)
	end

	function ThemeManager:LoadDefault()
		local defaultPath = self.Folder .. '/themes/default.txt'
		if isfile(defaultPath) then
			local name = self:ResolveName(readfile(defaultPath))
			if name then
				self:ApplyTheme(name)
				return
			end
		end
		self:ApplyTheme(self.DefaultTheme)
	end

	function ThemeManager:_orderedNames()
		local names, seen = {}, {}
		for _, name in ipairs(self.ThemeOrder) do
			if self.BuiltInThemes[name] and not seen[name] then
				seen[name] = true
				table.insert(names, name)
			end
		end
		local extra = {}
		for name in pairs(self.BuiltInThemes) do
			if not seen[name] then
				table.insert(extra, name)
			end
		end
		table.sort(extra)
		for _, name in ipairs(extra) do
			table.insert(names, name)
		end
		return names
	end

	function ThemeManager:BuildThemeManager(section)
		assert(self.Library, 'Must set ThemeManager.Library first!')

		section:AddDropdown({
			Title = "Theme List",
			Values = self:_orderedNames(),
			Default = self.DefaultTheme,
			Flag = "ThemeManager_ThemeList",
			Callback = function(value)
				self:ApplyTheme(value)
			end
		})

		section:AddButton({
			Title = "Set as Default Theme",
			Callback = function()
				local name = self.Library.Flags.ThemeManager_ThemeList
				if name then
					self:SaveDefault(name)
				end
			end
		})

		section:AddButton({
			Title = "Reset to Default Theme",
			Callback = function()
				pcall(delfile, self.Folder .. '/themes/default.txt')
				self:ApplyTheme(self.DefaultTheme)
			end
		})

		self:LoadDefault()
	end

	ThemeManager:BuildFolderTree()
end

getgenv().CallistoThemeManager = ThemeManager
return ThemeManager
