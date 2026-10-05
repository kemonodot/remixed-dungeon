local buff = require "scripts/lib/buff"
return buff.init{
    id = "ProofSparks",
    tags = {"MOVE", "TILE", "DAMAGE"},
    trigger = "MOVE",
    condition = function(bridge, event) return bridge:isHero(event:getActor()) end,
    effect = function(bridge, event) bridge:leaveSpark(event:getFrom(), 4, 3) end,
    desc = function()
        return {icon=9, name="Footprint Sparks", info="Moving one tile leaves a spark for 3 turns. An enemy entering it takes 4 special damage."}
    end,
    act = function(self, marker) marker:spend(1) end
}
