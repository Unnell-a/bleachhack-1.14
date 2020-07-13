package bleach.hack.module.mods;

import bleach.hack.event.events.EventRender;
import bleach.hack.module.Category;
import bleach.hack.module.Module;
import bleach.hack.utils.RenderUtils;
import com.google.common.eventbus.Subscribe;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.item.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RayTraceContext;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

public class Trajectories extends Module {
    public Trajectories() {
        super("Trajectories", KEY_UNBOUND, Category.COMBAT, "Predicts the flight path of arrows and throwable items");
    }

    @Subscribe
    public void onRender(EventRender event) {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2);
        GL11.glDisable(GL11.GL_LIGHTING);

        Camera camera = BlockEntityRenderDispatcher.INSTANCE.camera;
        GL11.glRotated(MathHelper.wrapDegrees(camera.getPitch()), 1, 0, 0);
        GL11.glRotated(MathHelper.wrapDegrees(camera.getYaw() + 180.0), 0, 1, 0);

        ArrayList<Vec3d> path = getPath(event.partialTicks);
        Vec3d camPos = BlockEntityRenderDispatcher.INSTANCE.camera.getPos();

        drawLine(path, camPos);

        if (!path.isEmpty()) {
            Vec3d end = path.get(path.size() - 1);
            drawEndOfLine(end, camPos);
        }

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
    }

    private void drawLine(ArrayList<Vec3d> path, Vec3d camPos) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glColor4f(0, 1, 0, 0.75F);

        for (Vec3d point : path)
            GL11.glVertex3d(point.x - camPos.x, point.y - camPos.y,
                    point.z - camPos.z);

        GL11.glEnd();
    }

    private void drawEndOfLine(Vec3d end, Vec3d camPos) {
        double renderX = end.x - camPos.x;
        double renderY = end.y - camPos.y;
        double renderZ = end.z - camPos.z;

        GL11.glPushMatrix();
        GL11.glTranslated(renderX - 0.25, renderY - 0.25, renderZ - 0.25);
        GL11.glScaled(1, 1, 1);

        GL11.glColor4f(0, 1, 0, 0.25F);
        RenderUtils.drawSolidBox();

        GL11.glColor4f(0, 1, 0, 0.75F);
        RenderUtils.drawOutlinedBox();

        GL11.glPopMatrix();
    }

    private ArrayList<Vec3d> getPath(float partialTicks) {
        ClientPlayerEntity player = mc.player;
        ArrayList<Vec3d> path = new ArrayList<>();

        ItemStack stack = player.getMainHandStack();
        Item item = stack.getItem();

        // check if item is throwable
        if (stack.isEmpty() || !isThrowable(item))
            return path;

        // calculate starting position
        double arrowPosX = player.lastRenderX
                + (player.getX() - player.lastRenderX) * partialTicks
                - Math.cos(Math.toRadians(player.yaw)) * 0.16;

        double arrowPosY = player.lastRenderY
                + (player.getY() - player.lastRenderY) * partialTicks
                + player.getStandingEyeHeight() - 0.1;

        double arrowPosZ = player.lastRenderZ
                + (player.getZ() - player.lastRenderZ) * partialTicks
                - Math.sin(Math.toRadians(player.yaw)) * 0.16;

        // Motion factor. Arrows go faster than snowballs and all that...
        double arrowMotionFactor = item instanceof RangedWeaponItem ? 1.0 : 0.4;

        double yaw = Math.toRadians(player.yaw);
        double pitch = Math.toRadians(player.pitch);

        // calculate starting motion
        double arrowMotionX =
                -Math.sin(yaw) * Math.cos(pitch) * arrowMotionFactor;
        double arrowMotionY = -Math.sin(pitch) * arrowMotionFactor;
        double arrowMotionZ =
                Math.cos(yaw) * Math.cos(pitch) * arrowMotionFactor;

        // 3D Pythagorean theorem. Returns the length of the arrowMotion vector.
        double arrowMotion = Math.sqrt(arrowMotionX * arrowMotionX
                + arrowMotionY * arrowMotionY + arrowMotionZ * arrowMotionZ);

        arrowMotionX /= arrowMotion;
        arrowMotionY /= arrowMotion;
        arrowMotionZ /= arrowMotion;

        // apply bow charge
        if (item instanceof RangedWeaponItem) {
            float bowPower = (72000 - player.getItemUseTimeLeft()) / 20.0f;
            bowPower = (bowPower * bowPower + bowPower * 2.0f) / 3.0f;

            if (bowPower > 1 || bowPower <= 0.1F)
                bowPower = 1;

            bowPower *= 3F;
            arrowMotionX *= bowPower;
            arrowMotionY *= bowPower;
            arrowMotionZ *= bowPower;

        } else {
            arrowMotionX *= 1.5;
            arrowMotionY *= 1.5;
            arrowMotionZ *= 1.5;
        }

        double gravity = getProjectileGravity(item);
        Vec3d eyesPos = new Vec3d(player.getX(), player.getY() + player.getEyeHeight(player.getPose()), player.getZ());
        ;

        for (int i = 0; i < 1000; i++) {
            // add to path
            Vec3d arrowPos = new Vec3d(arrowPosX, arrowPosY, arrowPosZ);
            path.add(arrowPos);

            // apply motion
            arrowPosX += arrowMotionX * 0.1;
            arrowPosY += arrowMotionY * 0.1;
            arrowPosZ += arrowMotionZ * 0.1;

            // apply air friction
            arrowMotionX *= 0.999;
            arrowMotionY *= 0.999;
            arrowMotionZ *= 0.999;

            // apply gravity
            arrowMotionY -= gravity * 0.1;

            // check for collision
            RayTraceContext context = new RayTraceContext(eyesPos, arrowPos,
                    RayTraceContext.ShapeType.COLLIDER,
                    RayTraceContext.FluidHandling.NONE, mc.player);
            if (mc.world.rayTrace(context).getType() != HitResult.Type.MISS)
                break;
        }

        return path;
    }

    private double getProjectileGravity(Item item) {
        if (item instanceof BowItem || item instanceof CrossbowItem)
            return 0.05;

        if (item instanceof PotionItem)
            return 0.4;

        if (item instanceof FishingRodItem)
            return 0.15;

        if (item instanceof TridentItem)
            return 0.015;

        return 0.03;
    }

    private boolean isThrowable(Item item) {
        return item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof SnowballItem || item instanceof EggItem
                || item instanceof EnderPearlItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof FishingRodItem || item instanceof TridentItem;
    }
}