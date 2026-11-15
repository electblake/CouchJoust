local frame = 0
local machine = manager.machine
local memory = machine.devices[":maincpu"].spaces["program"]
local scenario = os.getenv("JOUST_TRACE_SCENARIO") or "movement"
local output = io.open("artifacts/reference/player-" .. scenario .. ".csv", "w")
output:write("tick,x,yQ,vy,q,remainder,ground,flapTimer,stride,acceleration,facing,randomHigh,randomLow\n")
local activeTick = -1
local previousEnemies = -1
for tag,port in pairs(machine.ioport.ports) do
  for name,field in pairs(port.fields) do print("INPUT " .. tag .. " " .. name .. " " .. field.mask) end
end
emu.register_frame_done(function()
  frame = frame + 1
  local ports = machine.ioport.ports
  ports[":IN2"].fields["Advance"]:set_value(frame == 600 and 1 or 0)
  ports[":IN2"].fields["Coin 1"]:set_value(frame >= 900 and frame < 910 and 1 or 0)
  ports[":IN0"].fields["1 Player Start"]:set_value(frame >= 960 and frame < 970 and 1 or 0)
  local p = memory:read_u16(0xa073)
  if frame > 960 and memory:read_u8(0xa090) ~= 0 and p ~= 0 then
    activeTick = activeTick + 1
    local enemies = memory:read_u8(0xa0a1)
    if enemies ~= previousEnemies then
      print("ENEMY_COUNT " .. activeTick .. " " .. enemies)
      previousEnemies = enemies
    end
    output:write(string.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n",activeTick,memory:read_i16(p+7),memory:read_u16(p+11),memory:read_i16(p+17),memory:read_i8(p+20),memory:read_u8(p+22),memory:read_u16(p+32),memory:read_u8(p+19),memory:read_u8(p+22),memory:read_u8(p+21),memory:read_u8(p+15),memory:read_u8(0xa00d),memory:read_u8(0xa00e)))
    if scenario == "movement" then
      ports[":INP1"].fields["P1 Right"]:set_value(activeTick < 40 and 1 or 0)
      ports[":INP1"].fields["P1 Button 1"]:set_value(activeTick >= 40 and activeTick < 60 and 1 or 0)
    elseif scenario == "flight" then
      ports[":INP1"].fields["P1 Right"]:set_value(activeTick % 80 < 40 and 1 or 0)
      ports[":INP1"].fields["P1 Left"]:set_value(activeTick % 80 >= 40 and 1 or 0)
      ports[":INP1"].fields["P1 Button 1"]:set_value(activeTick % 8 < 4 and 1 or 0)
    elseif scenario == "skid" then
      ports[":INP1"].fields["P1 Right"]:set_value((activeTick < 55 or activeTick >= 120 and activeTick < 175) and 1 or 0)
      ports[":INP1"].fields["P1 Left"]:set_value(activeTick >= 55 and activeTick < 120 and 1 or 0)
      ports[":INP1"].fields["P1 Button 1"]:set_value(activeTick >= 220 and activeTick < 230 and 1 or 0)
    end
    if activeTick == 120 then machine.video:snapshot() end
    if activeTick == 300 then output:close();machine:exit() end
  end
  if frame == 2400 then machine.video:snapshot();output:close();machine:exit() end
end)
