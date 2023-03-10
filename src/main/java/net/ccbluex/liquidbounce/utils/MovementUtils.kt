// Destiny made by ChengFeng
package net.ccbluex.liquidbounce.utils

import net.ccbluex.liquidbounce.event.MoveEvent
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.potion.Potion
import net.minecraft.util.AxisAlignedBB
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


object MovementUtils : MinecraftInstance() {
    private val frictionValues: java.util.ArrayList<Double> = ArrayList()


    @JvmStatic
    fun getSpeed(): Float {
        return sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ).toFloat()
    }

    fun getSpeed(motionX: Double, motionZ: Double): Double {
        return Math.sqrt(motionX * motionX + motionZ * motionZ)
    }
    fun getRawDirection(): Float {
        return MovementUtils.getRawDirectionRotation(
            mc.thePlayer.rotationYaw,
            mc.thePlayer.moveStrafing,
            mc.thePlayer.moveForward
        )
    }
    @JvmStatic
    fun strafe() {
        strafe(getSpeed())
    }

    @JvmStatic
    fun move() {
        move(getSpeed())
    }

    @JvmStatic
    fun getSpeed2(): Double {
        return getSpeed2(mc.thePlayer)
    }

    @JvmStatic
    fun getSpeed2(entity: Entity): Double {
        return sqrt(entity.motionX * entity.motionX + entity.motionZ * entity.motionZ)
    }

    @JvmStatic
    fun isMoving(): Boolean {
        return mc.thePlayer != null && (mc.thePlayer.movementInput.moveForward != 0f || mc.thePlayer.movementInput.moveStrafe != 0f)
    }

    @JvmStatic
    fun hasMotion(): Boolean {
        return mc.thePlayer.motionX != 0.0 && mc.thePlayer.motionZ != 0.0 && mc.thePlayer.motionY != 0.0
    }

    @JvmStatic
    fun strafe(speed: Float) {
        if (!isMoving()) return
        val yaw = direction
        mc.thePlayer.motionX = -sin(yaw) * speed
        mc.thePlayer.motionZ = cos(yaw) * speed
    }

    @JvmStatic
    fun move(speed: Float) {
        if (!isMoving()) return
        val yaw = direction
        mc.thePlayer.motionX += -sin(yaw) * speed
        mc.thePlayer.motionZ += cos(yaw) * speed
    }

    @JvmStatic
    fun defaultSpeed(entity: EntityLivingBase, effectBoost: Double): Double {
        var baseSpeed = 0.2873
        if (entity.isPotionActive(Potion.moveSpeed)) {
            val amplifier = entity.getActivePotionEffect(Potion.moveSpeed).amplifier
            baseSpeed *= 1.0 + effectBoost * (amplifier + 1)
        }
        return baseSpeed
    }

    @JvmStatic
    fun defaultSpeed(): Double {
        return defaultSpeed(mc.thePlayer)
    }

    @JvmStatic
    fun defaultSpeed(entity: EntityLivingBase?): Double {
        return defaultSpeed(entity!!, 0.2)
    }

    @JvmStatic
    fun calculateFriction(moveSpeed: Double, lastDist: Double, baseMoveSpeedRef: Double): Double {
        frictionValues.clear()
        frictionValues.add(lastDist - lastDist / 159.9999985)
        frictionValues.add(lastDist - (moveSpeed - lastDist) / 33.3)
        val materialFriction =
            if (mc.thePlayer.isInWater) 0.8899999856948853 else if (mc.thePlayer.isInLava) 0.5350000262260437 else 0.9800000190734863
        frictionValues.add(lastDist - baseMoveSpeedRef * (1.0 - materialFriction))
        return Collections.min(frictionValues as Collection<Double>)!!
    }

    @JvmStatic
    fun limitSpeed(speed: Float) {
        val yaw = direction
        val maxXSpeed = -sin(yaw) * speed
        val maxZSpeed = cos(yaw) * speed
        if (mc.thePlayer.motionX > maxZSpeed) {
            mc.thePlayer.motionX = maxXSpeed
        }
        if (mc.thePlayer.motionZ > maxZSpeed) {
            mc.thePlayer.motionZ = maxZSpeed
        }
    }

    /**
     * make player move slowly like when using item
     * @author liulihaocai
     */
    @JvmStatic
    fun limitSpeedByPercent(percent: Float) {
        mc.thePlayer.motionX *= percent
        mc.thePlayer.motionZ *= percent
    }

    @JvmStatic
    fun forward(length: Double) {
        val yaw = Math.toRadians(mc.thePlayer.rotationYaw.toDouble())
        mc.thePlayer.setPosition(
            mc.thePlayer.posX + -sin(yaw) * length,
            mc.thePlayer.posY,
            mc.thePlayer.posZ + cos(yaw) * length
        )
    }

    val direction: Double
        get() {
            var rotationYaw = mc.thePlayer.rotationYaw
            if (mc.thePlayer.moveForward < 0f) rotationYaw += 180f
            var forward = 1f
            if (mc.thePlayer.moveForward < 0f) forward = -0.5f else if (mc.thePlayer.moveForward > 0f) forward = 0.5f
            if (mc.thePlayer.moveStrafing > 0f) rotationYaw -= 90f * forward
            if (mc.thePlayer.moveStrafing < 0f) rotationYaw += 90f * forward
            return Math.toRadians(rotationYaw.toDouble())
        }

    var bps = 0.0
        private set
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0

    @JvmStatic
    fun setMotion(speed: Double) {
        var forward = mc.thePlayer.movementInput.moveForward.toDouble()
        var strafe = mc.thePlayer.movementInput.moveStrafe.toDouble()
        var yaw = mc.thePlayer.rotationYaw
        if (forward == 0.0 && strafe == 0.0) {
            mc.thePlayer.motionX = 0.0
            mc.thePlayer.motionZ = 0.0
        } else {
            if (forward != 0.0) {
                if (strafe > 0.0) {
                    yaw += (if (forward > 0.0) -45 else 45).toFloat()
                } else if (strafe < 0.0) {
                    yaw += (if (forward > 0.0) 45 else -45).toFloat()
                }
                strafe = 0.0
                if (forward > 0.0) {
                    forward = 1.0
                } else if (forward < 0.0) {
                    forward = -1.0
                }
            }
            val cos = cos(Math.toRadians((yaw + 90.0f).toDouble()))
            val sin = sin(Math.toRadians((yaw + 90.0f).toDouble()))
            mc.thePlayer.motionX = (forward * speed * cos +
                    strafe * speed * sin)
            mc.thePlayer.motionZ = (forward * speed * sin -
                    strafe * speed * cos)
        }
    }

    @JvmStatic
    fun updateBlocksPerSecond() {
        if (mc.thePlayer == null || mc.thePlayer.ticksExisted < 1) {
            bps = 0.0
        }
        val distance = mc.thePlayer.getDistance(lastX, lastY, lastZ)
        lastX = mc.thePlayer.posX
        lastY = mc.thePlayer.posY
        lastZ = mc.thePlayer.posZ
        bps = distance * (20 * mc.timer.timerSpeed)
    }

    @JvmStatic
    fun setSpeed(
        moveEvent: MoveEvent,
        moveSpeed: Double,
        pseudoYaw: Float,
        pseudoStrafe: Double,
        pseudoForward: Double
    ) {
        var forward = pseudoForward
        var strafe = pseudoStrafe
        var yaw = pseudoYaw
        if (forward == 0.0 && strafe == 0.0) {
            moveEvent.z = 0.0
            moveEvent.x = 0.0
        } else {
            if (forward != 0.0) {
                if (strafe > 0.0) {
                    yaw += (if (forward > 0.0) -45 else 45).toFloat()
                } else if (strafe < 0.0) {
                    yaw += (if (forward > 0.0) 45 else -45).toFloat()
                }
                strafe = 0.0
                if (forward > 0.0) {
                    forward = 1.0
                } else if (forward < 0.0) {
                    forward = -1.0
                }
            }
            val cos = Math.cos(Math.toRadians((yaw + 90.0f).toDouble()))
            val sin = Math.sin(Math.toRadians((yaw + 90.0f).toDouble()))
            moveEvent.x = forward * moveSpeed * cos + strafe * moveSpeed * sin
            moveEvent.z = forward * moveSpeed * sin - strafe * moveSpeed * cos
        }
    }
    fun getRawDirectionRotation(yaw: Float, pStrafe: Float, pForward: Float): Float {
        var rotationYaw = yaw
        if (pForward < 0f) rotationYaw += 180f
        var forward = 1f
        if (pForward < 0f) forward = -0.5f else if (pForward > 0f) forward = 0.5f
        if (pStrafe > 0f) rotationYaw -= 90f * forward
        if (pStrafe < 0f) rotationYaw += 90f * forward
        return rotationYaw
    }
    fun getJumpBoostModifier(baseJumpHeight: Double): Double {
        var baseJumpHeight = baseJumpHeight
        if (mc.thePlayer.isPotionActive(Potion.jump)) {
            val amplifier = mc.thePlayer.getActivePotionEffect(Potion.jump).amplifier
            baseJumpHeight += ((amplifier + 1).toFloat() * 0.1f).toDouble()
        }
        return baseJumpHeight
    }
    fun calculateGround(): Double {
        val playerBoundingBox = mc.thePlayer.entityBoundingBox
        var blockHeight = 1.0
        var ground = mc.thePlayer.posY
        while (ground > 0.0) {
            val customBox = AxisAlignedBB(
                playerBoundingBox.maxX,
                ground + blockHeight,
                playerBoundingBox.maxZ,
                playerBoundingBox.minX,
                ground,
                playerBoundingBox.minZ
            )
            if (mc.theWorld.checkBlockCollision(customBox)) {
                if (blockHeight <= 0.05) return ground + blockHeight
                ground += blockHeight
                blockHeight = 0.05
            }
            ground -= blockHeight
        }
        return 0.0
    }

    fun handleVanillaKickBypass() {
        val ground = calculateGround()
        run {
            var posY = mc.thePlayer.posY
            while (posY > ground) {
                mc.netHandler.addToSendQueue(C04PacketPlayerPosition(mc.thePlayer.posX, posY, mc.thePlayer.posZ, true))
                if (posY - 8.0 < ground) break // Prevent next step
                posY -= 8.0
            }
        }
        mc.netHandler.addToSendQueue(C04PacketPlayerPosition(mc.thePlayer.posX, ground, mc.thePlayer.posZ, true))
        var posY = ground
        while (posY < mc.thePlayer.posY) {
            mc.netHandler.addToSendQueue(C04PacketPlayerPosition(mc.thePlayer.posX, posY, mc.thePlayer.posZ, true))
            if (posY + 8.0 > mc.thePlayer.posY) break // Prevent next step
            posY += 8.0
        }
        mc.netHandler.addToSendQueue(
            C04PacketPlayerPosition(
                mc.thePlayer.posX,
                mc.thePlayer.posY,
                mc.thePlayer.posZ,
                true
            )
        )
    }
}