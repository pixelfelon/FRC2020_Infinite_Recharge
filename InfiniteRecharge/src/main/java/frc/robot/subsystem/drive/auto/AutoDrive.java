package frc.robot.subsystem.drive.auto;

import frc.robot.subsystem.drive.DriveSubsystem;
import frc.robot.subsystem.drive.Idle;
import frc.robot.subsystem.drive.RotationDrive;
import frc.robot.subsystem.drive.VelocityDrive;
import frc.robot.subsystem.drive.DriveSubsystem.DriveMethod;
import frc.robot.utils.CommandUtils;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.controller.RamseteController;
import edu.wpi.first.wpilibj.geometry.Pose2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.RamseteCommand;
import edu.wpi.first.wpilibj.trajectory.Trajectory;
import edu.wpi.first.wpilibj.trajectory.Trajectory.State;



public class AutoDrive extends RamseteCommand {
    private final DriveSubsystem DRIVE_SUBSYSTEM;

    private Timer timer;

    private final Trajectory TRAJ;



    public AutoDrive(DriveSubsystem driveSubsystem, Trajectory traj) {
        super(
            traj,
            driveSubsystem::getPose,
            driveSubsystem.getRAMSETEController(),
            driveSubsystem.getCharacterization(),
            driveSubsystem.getKinematics(),
            driveSubsystem::getWheelSpeeds,
            driveSubsystem.getLeftAutoPID(),
            driveSubsystem.getRightAutoPID(),
            driveSubsystem::tankVolts,
            driveSubsystem
        );

        DRIVE_SUBSYSTEM = driveSubsystem;
        timer = new Timer();
        TRAJ = traj;
    }

    public void initialize() {
        super.initialize();

        timer.reset();
        timer.start();
        //DRIVE_SUBSYSTEM.disable();
    }

    @Override
    public void execute() {
        super.execute();

        Pose2d actual = DRIVE_SUBSYSTEM.getNavigation().getPose();
        Pose2d desired = TRAJ.sample(timer.get()).poseMeters;

        SmartDashboard.putNumber("DriveSubsystem/actual x", actual.getTranslation().getX());
        SmartDashboard.putNumber("DriveSubsystem/actual y", actual.getTranslation().getY());
        SmartDashboard.putNumber("DriveSubsystem/actual theta", actual.getRotation().getDegrees());

        SmartDashboard.putNumber("DriveSubsystem/desired x", desired.getTranslation().getX());
        SmartDashboard.putNumber("DriveSubsystem/desired y", desired.getTranslation().getY());
        SmartDashboard.putNumber("DriveSubsystem/desired theta", desired.getRotation().getDegrees());
    }

}