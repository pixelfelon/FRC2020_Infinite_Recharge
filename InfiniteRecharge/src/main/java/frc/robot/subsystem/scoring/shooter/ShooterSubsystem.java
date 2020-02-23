package frc.robot.subsystem.scoring.shooter;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.config.Config;
import frc.robot.subsystem.BitBucketSubsystem;
import frc.robot.utils.math.MathUtils;
import frc.robot.utils.talonutils.MotorUtils;

import frc.robot.utils.data.filters.RunningAverageFilter;
import frc.robot.subsystem.scoring.shooter.ShooterConstants;
import frc.robot.subsystem.scoring.shooter.ball_management.BallManagementConstants;
import frc.robot.subsystem.scoring.shooter.ball_management.BallManagementSubsystem;
import frc.robot.subsystem.vision.VelocityPoint;
import frc.robot.subsystem.vision.VisionSubsystem;

import frc.robot.utils.data.filters.RunningAverageFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShooterSubsystem extends BitBucketSubsystem {

    //////////////////////////////////////////////////////////////////////////////
    // Variables

    // Booleans
    public boolean shooting = false;
    public boolean feeding = false;
    public boolean feederVelocityControl = false;
    public boolean shooterVelocityControl = false;

    private boolean upToSpeed = false;
    private boolean positionElevationSwitcherAlreadyPressed = false;

    // Integers
    private int targetPositionAzimuth_ticks;
    private int targetChangeAzimuth_ticks;

    private int targetPositionElevation_ticks;
    private int targetChangeElevation_ticks;

    // Floats
    // TODO float rootBeer = good
    private float[] positions = config.shooter.elevationPositions_deg;

    // Doubles
    private double absoluteDegreesToRotateAzimuth = 0.0;

    private double rightAzimuthSoftLimit_ticks;
    private double leftAzimuthSoftLimit_ticks;

    private double forwardElevationSoftLimit_ticks;
    private double backwardElevationSoftLimit_ticks;

    // Class Declarations
    RunningAverageFilter azimuthFilter = new RunningAverageFilter(ShooterConstants.FILTER_LENGTH);
    RunningAverageFilter elevationFilter = new RunningAverageFilter(ShooterConstants.FILTER_LENGTH);
    RunningAverageFilter feederFilter = new RunningAverageFilter(ShooterConstants.FEEDER_FILTER_LENGTH);

    public BallManagementSubsystem ballManagementSubsystem;
    private VisionSubsystem visionSubsystem;

    //////////////////////////////////////////////////////////////////////////////
    // Motors

    // Talons
    private WPI_TalonSRX azimuthMotor;
    private WPI_TalonSRX elevationMotor;
    private WPI_TalonFX ballPropulsionMotor;
    private WPI_TalonSRX feeder;

    // Neos

    //////////////////////////////////////////////////////////////////////////////
    // Methods

    public ShooterSubsystem(Config config, VisionSubsystem visionSubsystem) {
        super(config);
        this.visionSubsystem = visionSubsystem;

    }

    @Override
    public void initialize() {
        super.initialize();
        azimuthMotor = MotorUtils.makeSRX(config.shooter.azimuth);
        elevationMotor = MotorUtils.makeSRX(config.shooter.elevation);

        ballPropulsionMotor = MotorUtils.makeFX(config.shooter.shooter);
        ballPropulsionMotor.configOpenloopRamp(1);
        ballPropulsionMotor.configClosedloopRamp(0.75);
        feeder = MotorUtils.makeSRX(config.shooter.feeder);
        feeder.enableVoltageCompensation(true);
        feeder.configVoltageCompSaturation(ShooterConstants.MAX_VOLTS);
        feeder.selectProfileSlot(MotorUtils.velocitySlot, 0);
        feeder.setNeutralMode(NeutralMode.Brake);

        ballPropulsionMotor.selectProfileSlot(MotorUtils.velocitySlot, 0);

        if (config.enableBallManagementSubsystem) {
            ballManagementSubsystem = new BallManagementSubsystem(config);
            ballManagementSubsystem.initialize();
        }

        rightAzimuthSoftLimit_ticks = MathUtils.unitConverter(config.shooter.rightAzimuthSoftLimit_deg, 360,
                config.shooter.azimuth.ticksPerRevolution) / config.shooter.azimuthGearRatio;
        leftAzimuthSoftLimit_ticks = MathUtils.unitConverter(config.shooter.leftAzimuthSoftLimit_deg, 360,
                config.shooter.azimuth.ticksPerRevolution) / config.shooter.azimuthGearRatio;

        forwardElevationSoftLimit_ticks = MathUtils.unitConverter(config.shooter.forwardElevationSoftLimit_deg, 360,
                config.shooter.elevation.ticksPerRevolution) / config.shooter.elevationGearRatio;
        backwardElevationSoftLimit_ticks = MathUtils.unitConverter(config.shooter.backwardElevationSoftLimit_deg, 360,
                config.shooter.elevation.ticksPerRevolution) / config.shooter.elevationGearRatio;

        if (config.shooter.rightAzimuthSoftLimit_deg != -1 && config.shooter.leftAzimuthSoftLimit_deg != -1) {
            azimuthMotor.configForwardSoftLimitEnable(true);
            azimuthMotor.configForwardSoftLimitThreshold((int) rightAzimuthSoftLimit_ticks);

            azimuthMotor.configReverseSoftLimitEnable(true);
            azimuthMotor.configReverseSoftLimitThreshold((int) -leftAzimuthSoftLimit_ticks);
        }
        if (config.shooter.forwardElevationSoftLimit_deg != -1 && config.shooter.backwardElevationSoftLimit_deg != -1) {
            elevationMotor.configForwardSoftLimitEnable(true);
            elevationMotor.configForwardSoftLimitThreshold((int) forwardElevationSoftLimit_ticks);

            elevationMotor.configReverseSoftLimitEnable(true);
            elevationMotor.configReverseSoftLimitThreshold((int) -backwardElevationSoftLimit_ticks);
        }
    }

    @Override
    public void testInit() {

    }

    @Override
    public void testPeriodic() {
        // TODO Auto-generated method stub

    }

    @Override
    public void diagnosticsCheck() {
        // TODO Auto-generated method stub

    }

    @Override
    public void periodic(float deltaTime) {

        calculateAbsoluteDegreesToRotate();

        targetPositionAzimuth_ticks = (int) (targetPositionAzimuth_ticks + (targetChangeAzimuth_ticks * deltaTime));
        targetPositionElevation_ticks = (int) (targetPositionElevation_ticks
                + (targetChangeElevation_ticks * deltaTime));

        if (config.shooter.rightAzimuthSoftLimit_deg != -1 && config.shooter.leftAzimuthSoftLimit_deg != -1) {
            if (targetPositionAzimuth_ticks > rightAzimuthSoftLimit_ticks) {
                targetPositionAzimuth_ticks = (int) rightAzimuthSoftLimit_ticks;
            } else if (targetPositionAzimuth_ticks < -leftAzimuthSoftLimit_ticks) {
                targetPositionAzimuth_ticks = (int) -leftAzimuthSoftLimit_ticks;
            }
        }
        if (config.shooter.forwardElevationSoftLimit_deg != -1 && config.shooter.backwardElevationSoftLimit_deg != -1) {
            if (targetPositionElevation_ticks > forwardElevationSoftLimit_ticks) {
                targetPositionElevation_ticks = (int) forwardElevationSoftLimit_ticks;
            } else if (targetPositionElevation_ticks < -backwardElevationSoftLimit_ticks) {
                targetPositionElevation_ticks = (int) -backwardElevationSoftLimit_ticks;
            }
        }

        azimuthMotor.set(ControlMode.MotionMagic, targetPositionAzimuth_ticks);
        elevationMotor.set(ControlMode.MotionMagic, targetPositionElevation_ticks);
    }

    public void spinUp() {
        float targetShooterVelocity = (float) MathUtils
                .unitConverter(
                        SmartDashboard.getNumber(getName() + "/Shooter Velocity RPM",
                                ShooterConstants.DEFAULT_SHOOTER_VELOCITY_RPM),
                        600, config.shooter.shooter.ticksPerRevolution)
                * config.shooter.shooterGearRatio;
        double averageError = feederFilter.calculate((double) Math.abs(ballPropulsionMotor.getSelectedSensorVelocity() - targetShooterVelocity));
        // Spin up the feeder.
        if (averageError <= config.shooter.feederSpinUpDeadband_ticks) {
            feeder.set(SmartDashboard.getNumber(getName() + "/Feeder Output Percent",
                    ShooterConstants.FEEDER_OUTPUT_PERCENT));
            SmartDashboard.putString(getName() + "/Feeder State", "Feeding");
            upToSpeed = true;
        } else {
            upToSpeed = false;
            feeder.set(0);
            SmartDashboard.putString(getName() + "/Feeder State", "Cannot fire: Shooter hasn't been spun up!");
        }

        // Spin up the shooter.
        ballPropulsionMotor.set(ControlMode.Velocity, targetShooterVelocity);
        // ballPropulsionMotor.set(ControlMode.PercentOutput, (float)SmartDashboard.getNumber(getName() + "/Shooter %Output", 0.5));
        SmartDashboard.putString(getName() + "/Shooter State", "Shooting");
    }

    public void stopSpinningUp() {
        // Spin up the feeder.
        feeder.set(0);
        SmartDashboard.putString(getName() + "/Feeder State", "Doing Nothing");

        // Spin up the shooter.
        ballPropulsionMotor.set(0);
        SmartDashboard.putString(getName() + "/Shooter State", "Doing Nothing");

        upToSpeed = false;
    }

    public void spinBMS() {
        if (config.enableBallManagementSubsystem) {
            ballManagementSubsystem
                    .fire((float) SmartDashboard.getNumber(getName() + "/BallManagementSubsystem/Output Percent",
                            BallManagementConstants.BMS_OUTPUT_PERCENT));
        } else {
            SmartDashboard.putString("BallManagementSubsystem/State",
                    "Cannot fire: BallManagementSubsystem is not enabled.");
        }
    }

    public void holdFire() {
        if (config.enableBallManagementSubsystem) {
            ballManagementSubsystem.doNotFire();
        }
    }

    public void rotate(double spinRateAzimuth, double spinRateElevation) {
        // Turn turret at a quantity of degrees per second configurable in the smart
        // dashboard.
        double smartDashboardTurnRateTicksAzimuth = MathUtils
                .unitConverter(
                        SmartDashboard.getNumber(getName() + "/Azimuth Turn Rate",
                                config.shooter.defaultAzimuthTurnVelocity_deg),
                        360, config.shooter.azimuth.ticksPerRevolution)
                / config.shooter.azimuthGearRatio;

        double smartDashboardTurnRateTicksElevation = MathUtils.unitConverter(
                SmartDashboard.getNumber(getName() + "/Elevation Turn Rate",
                        config.shooter.defaultElevationTurnVelocity_deg),
                360, config.shooter.elevation.ticksPerRevolution) / config.shooter.elevationGearRatio;

        // Target position changes by this number every time periodic is called.
        targetChangeAzimuth_ticks = (int) (smartDashboardTurnRateTicksAzimuth * spinRateAzimuth);
        targetChangeElevation_ticks = (int) (smartDashboardTurnRateTicksElevation * spinRateElevation);
    }

    public void rotateToDeg(double targetPointAzimuth, double targetPointElevation) {
        double targetPointTicksAzimuth = MathUtils.unitConverter(targetPointAzimuth, 360,
                config.shooter.azimuth.ticksPerRevolution) / config.shooter.azimuthGearRatio;

        double targetPointTicksElevation = MathUtils.unitConverter(targetPointElevation, 360,
                config.shooter.elevation.ticksPerRevolution) / config.shooter.elevationGearRatio;

        targetPositionAzimuth_ticks = (int) (targetPointTicksAzimuth);
        targetChangeAzimuth_ticks = 0;

        targetPositionElevation_ticks = (int) (targetPointTicksElevation);
        targetChangeElevation_ticks = 0;
    }

    public double getAzimuthDeg() {
        double encoderDeg = MathUtils.unitConverter(azimuthMotor.getSelectedSensorPosition(),
                config.shooter.azimuth.ticksPerRevolution, 360.0);
        double turretDeg = encoderDeg * config.shooter.azimuthGearRatio;
        return turretDeg;
    }

    public double getElevationDeg() {
        double encoderDeg = MathUtils.unitConverter(elevationMotor.getSelectedSensorPosition(),
                config.shooter.elevation.ticksPerRevolution, 360.0);
        double turretDeg = encoderDeg * config.shooter.elevationGearRatio;
        return turretDeg;
    }

    /*
     * Returns target degrees of turret given an offset
     */
    public double getTargetAzimuthDegGivenOffset(double offset) {
        return getAzimuthDeg() + offset;
    }

    public double getTargetElevationDegGivenOffset(double offset) {
        return getElevationDeg() + offset;
    }

    public void autoAimAzimuth() {
        rotateToDeg(absoluteDegreesToRotateAzimuth, getElevationDeg());
    }

    public void autoAimElevation() {
        rotateToDeg(getAzimuthDeg(), calculateAbsoluteDegreesElevation());
    }

    public void autoAimVelocity() {
        double feetPerSecVelocity = calculateAbsoluteVelocity_in() / 12;
        double ticksVelocity = (feetPerSecVelocity * 12 * config.shooter.shooter.ticksPerRevolution)
                / (ShooterConstants.SHOOTER_FLYWHEEL_RADIUS * 2 * Math.PI * 10);

        ballPropulsionMotor.set(ControlMode.Velocity, ticksVelocity);

        // TODO: do this stuff empirically (yay!)
        // the math rn isn' rly accurate
        //  as u get closer decrease basically, as u get further, increase.
        // tho obv we'll test it and see
    }

    public void autoAim() {
        // autoAimAzimuth();
        autoAimElevation();
        // autoAimVelocity();
    }

    public boolean withinRange(double number, double min, double max) {
        return number >= min && number <= max;
    }

    public void calculateAbsoluteDegreesToRotate() {
        boolean validTarget = visionSubsystem.getValidTarget();
        if (validTarget) {
            double tx = visionSubsystem.getTx();
            double degrees = getTargetAzimuthDegGivenOffset(tx);

            // We believed the offset and thus the degrees might change, causing the robot
            // to possibly oscillate about its target. To prevent this, take an average.
            // Didn't make a difference, so we've disabled it. But code remains in case we
            // want
            // to use it again.

            // If enabled in the constants file, calculate the average of the last values
            // passed in (up to what FILTER_LENGTH is in ShooterConstants.java).
            absoluteDegreesToRotateAzimuth = ShooterConstants.USE_AZIMUTH_FILTER ? azimuthFilter.calculate(degrees) : degrees;
        }
    }

    // TODO: empirically test this
        // do the distance thing here
        // decreases as u get closer
        // increases as u get it farther
    public double calculateAbsoluteDegreesElevation() {
        boolean validTarget = visionSubsystem.getValidTarget();
        if (validTarget) {
            double ty = visionSubsystem.getTy();
            // double degrees = getTargetElevationDegGivenOffset(ty);
            double distance = visionSubsystem.approximateDistanceFromTarget(ty);

            List<VelocityPoint> velocityPoints = new ArrayList<VelocityPoint>();
            // add values here
 
            int closestIndex = Collections.binarySearch(velocityPoints, new VelocityPoint(distance, 0, 0));
            // TODO: interpolate between closestIndex and our thing
            return velocityPoints.get(closestIndex).getElevationAngle_deg();
            
            // final double absoluteDegreesElevation = degrees + 40;
            // return absoluteDegreesElevation;
        }
        return 0.0;
    }

    // TODO: empirically test this
    public double calculateAbsoluteVelocity_in() {
        boolean validTarget = visionSubsystem.getValidTarget();
        if (validTarget) {
            double ty = visionSubsystem.getTy();
            double distance = visionSubsystem.approximateDistanceFromTarget(ty);

            List<VelocityPoint> velocityPoints = new ArrayList<VelocityPoint>();
            // add values here
 
            int closestIndex = Collections.binarySearch(velocityPoints, new VelocityPoint(distance, 0, 0));
            // TODO: interpolate between closestIndex and our thing
            return velocityPoints.get(closestIndex).getElevationAngle_deg();

        }
        return 0.0;
    }

    public void nextPositionElevation() {
        for (int i = 0; i < positions.length; i++) {
            int selectedPositionNumber_ticks = (int) (MathUtils.unitConverter(positions[i], 360,
                    config.shooter.elevation.ticksPerRevolution) / config.shooter.elevationGearRatio);
            if (targetPositionElevation_ticks < selectedPositionNumber_ticks
                    && positionElevationSwitcherAlreadyPressed == false) {
                targetPositionElevation_ticks = (int) (selectedPositionNumber_ticks);
                positionElevationSwitcherAlreadyPressed = true;
                break;
            }
        }
    }

    public void lastPositionElevation() {
        for (int i = positions.length - 1; i >= 0; i--) {
            int selectedPositionNumber_ticks = (int) (MathUtils.unitConverter(positions[i], 360,
                    config.shooter.elevation.ticksPerRevolution) / config.shooter.elevationGearRatio);
            if (targetPositionElevation_ticks > selectedPositionNumber_ticks
                    && positionElevationSwitcherAlreadyPressed == false) {
                targetPositionElevation_ticks = (int) (selectedPositionNumber_ticks);
                positionElevationSwitcherAlreadyPressed = true;
                break;
            }
        }
    }

    public void resetPositionElevationSwitcher() {
        positionElevationSwitcherAlreadyPressed = false;
    }

    public double getTargetAzimuthDeg() {
        return MathUtils.unitConverter(targetPositionAzimuth_ticks, 360, config.shooter.azimuth.ticksPerRevolution)
                / config.shooter.azimuthGearRatio;
    }

    public double getTargetElevationDeg() {
        return MathUtils.unitConverter(targetPositionElevation_ticks, 360, config.shooter.elevation.ticksPerRevolution)
                / config.shooter.elevationGearRatio;
    }

    public boolean isUpToSpeed() {
        return upToSpeed;
    }

    @Override
    protected void dashboardInit() {
        super.dashboardInit();
        SmartDashboard.putNumber(getName() + "/Shooter Velocity RPM", ShooterConstants.DEFAULT_SHOOTER_VELOCITY_RPM);
        SmartDashboard.putNumber(getName() + "/Feeder Output Percent", ShooterConstants.FEEDER_OUTPUT_PERCENT);
        SmartDashboard.putNumber(getName() + "/Azimuth Turn Rate", config.shooter.defaultAzimuthTurnVelocity_deg);
        SmartDashboard.putNumber(getName() + "/Elevation Turn Rate", config.shooter.defaultAzimuthTurnVelocity_deg);
        SmartDashboard.putNumber(getName() + "/Dashboard Elevation Target", 10);


        SmartDashboard.putNumber(getName() + "/Shooter %Output", 0.5); // TODO TEMPORARY

    }

    @Override
    public void dashboardPeriodic(float deltaTime) {
        // Put the outputs on the smart dashboard.
        SmartDashboard.putNumber(getName() + "/Shooter Output", ballPropulsionMotor.getMotorOutputPercent());
        SmartDashboard.putNumber(getName() + "/Feeder Output", feeder.getMotorOutputPercent());
        SmartDashboard.putNumber(getName() + "/Shooter Velocity Ticks",
                ballPropulsionMotor.getSelectedSensorVelocity());
        SmartDashboard.putNumber(getName() + "/Shooter Velocity Target", ballPropulsionMotor.getClosedLoopTarget());
        SmartDashboard.putNumber(getName() + "/Shooter Velocity Error", ballPropulsionMotor.getClosedLoopError());

        SmartDashboard.putNumber(getName() + "/Shooter Velocity Current RPM",
                MathUtils.unitConverter(ballPropulsionMotor.getSelectedSensorVelocity(), 600,
                        config.shooter.shooter.ticksPerRevolution) * config.shooter.shooterGearRatio);
        SmartDashboard.putNumber(getName() + "/Shooter Velocity Target RPM",
                MathUtils.unitConverter(ballPropulsionMotor.getClosedLoopTarget(), 600,
                        config.shooter.shooter.ticksPerRevolution) * config.shooter.shooterGearRatio);
        SmartDashboard.putNumber(getName() + "/Shooter Velocity Error RPM",
                MathUtils.unitConverter(ballPropulsionMotor.getClosedLoopError(), 600,
                        config.shooter.shooter.ticksPerRevolution) * config.shooter.shooterGearRatio);

        SmartDashboard.putNumber(getName() + "/Target Position ", targetPositionAzimuth_ticks);
        SmartDashboard.putNumber(getName() + "/Absolute Degrees to Rotate", absoluteDegreesToRotateAzimuth);
        SmartDashboard.putNumber(getName() + "/Azimuth Position ", azimuthMotor.getSelectedSensorPosition());

        SmartDashboard.putNumber(getName() + "/Azimuth Target Position Deg ",
                MathUtils.unitConverter(targetPositionAzimuth_ticks, config.shooter.azimuth.ticksPerRevolution, 360)
                        * config.shooter.azimuthGearRatio);

        SmartDashboard.putNumber(getName() + "/Azimuth Position Deg ",
                MathUtils.unitConverter(azimuthMotor.getSelectedSensorPosition(),
                        config.shooter.azimuth.ticksPerRevolution, 360) * config.shooter.azimuthGearRatio);

        SmartDashboard.putNumber(getName() + "/Elevation Position Deg ",
                MathUtils.unitConverter(elevationMotor.getSelectedSensorPosition(),
                        config.shooter.elevation.ticksPerRevolution, 360) * config.shooter.elevationGearRatio);

        SmartDashboard.putNumber(getName() + "/Elevation Target Position ", targetPositionElevation_ticks);
        SmartDashboard.putNumber(getName() + "/Elevation Position ", elevationMotor.getSelectedSensorPosition());

        SmartDashboard.putNumber(getName() + "/Elevation Target Position Deg ",
                MathUtils.unitConverter(targetPositionElevation_ticks, config.shooter.elevation.ticksPerRevolution, 360)
                        * config.shooter.elevationGearRatio);
        
        SmartDashboard.putNumber(getName() + "/Absolute Degrees Elevation", calculateAbsoluteDegreesElevation());
        
        SmartDashboard.putNumber(getName() + "/Falcon temperature", (32 + 1.8*ballPropulsionMotor.getTemperature()));
    }
    public void disable(){
        azimuthMotor.set(0);
        elevationMotor.set(0);
        ballPropulsionMotor.set(0);
        feeder.set(0);

        upToSpeed = false;
        positionElevationSwitcherAlreadyPressed = false;

        targetPositionAzimuth_ticks = 0;
        targetChangeAzimuth_ticks = 0;
        targetPositionElevation_ticks = 0;
        targetChangeElevation_ticks = 0;
        absoluteDegreesToRotateAzimuth = 0;
    }

    public void zeroElevationSensor(){
        elevationMotor.setSelectedSensorPosition(0);
    }
}
