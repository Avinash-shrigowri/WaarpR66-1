/**
 * This file is part of GoldenGate Project (named also GoldenGate or GG).
 * 
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 * 
 * All GoldenGate Project is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * GoldenGate is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * GoldenGate . If not, see <http://www.gnu.org/licenses/>.
 */
package openr66.context.task;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import openr66.context.ErrorCode;
import openr66.context.R66Result;
import openr66.context.R66Session;
import openr66.context.task.exception.OpenR66RunnerErrorException;
import openr66.database.data.DbTaskRunner;

/**
 * Reschedule Transfer task to a time delayed by the specified number of milliseconds, 
 * if the error code is one of the specified codes and the optional intervals of date are 
 * compatible with the new time schedule<br><br>
 * 
 * Result of arguments will be as following options (the two first are
 * mandatory):<br>
 * <br>
 * 
 * "-delay ms" where ms is the added number of ms on current time before retry
 * on schedule<br>
 * "-case errorCode,errorCode,..." where errorCode is one of the following error
 * of the current transfer (either literal or code in 1 character:<br>
 * ConnectionImpossible(C), ServerOverloaded(l), BadAuthent(A), ExternalOp(E),
 * TransferError(T), MD5Error(M), Disconnection(D), RemoteShutdown(r),
 * FinalOp(F), Unimplemented(U), Shutdown(S), RemoteError(R), Internal(I),
 * StoppedTransfer(H), CanceledTransfer(K), Warning(W), Unknown(-),
 * QueryAlreadyFinished(Q), QueryStillRunning(s), NotKnownHost(N),
 * QueryRemotelyUnknown(u), FileNotFound(f), CommandNotFound(c),
 * PassThroughMode(p)<br>
 * <br>
 * "-between start;end" and/or "-notbetween start;end" (multiple times are
 * allowed, start or end can be not set) and where start and stop are in the
 * following format:<br>
 * Yn:Mn:Dn:Hn:mn:Sn where n is a number for each time specification, each
 * specification is optional, as Y=Year, M=Month, D=Day, H=Hour, m=minute,
 * s=second.<br>Format can be X+n, X-n, X=n or Xn where X+-n means 
 * adding/subtracting n to current date value, while X=n or Xn means setting exact value<br>
 * If one time specification is not set, it is based on the current date.<br>
 * <br>
 * If "-notbetween" is specified, the planned date must not be in the area.<br>
 * If "-between" is specified, the planned date must be found in any such specified areas (could be in any of the occurrence). 
 * If not specified, it only depends on "-notbetween".<br>
 * If none is specified, the planned date is always valid.<br>
 * <br>
 * 
 * Note that if a previous called to a reschedule was done for this attempt and was successful,
 * the following calls will be ignored.<br>
 * <br>
 * 
 * In case start > end, end will be +1 day<br>
 * In case start and end < current planned date, both will have +1 day.<br>
 * <br>
 * 
 * Example: -delay 3600000 -case ConnectionImpossible,ServerOverloaded,Shutdown
 * -notbetween H7:m0:S0;H19:m0:S0 -notbetween H1:m0:S0;H=3:m0:S0<br>
 * means retry in case of error during initialization of connection in 1 hour if
 * not between 7AM to 7PM and not between 1AM to 3AM.<br>
 * 
 * @author Frederic Bregier
 * 
 */
public class RescheduleTransferTask extends AbstractTask {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(RescheduleTransferTask.class);

    protected long newdate = 0;

    protected Calendar newDate = null;

    /**
     * @param argRule
     * @param delay
     * @param argTransfer
     * @param session
     */
    public RescheduleTransferTask(String argRule, int delay,
            String argTransfer, R66Session session) {
        super(TaskType.RESCHEDULE, delay, argRule, argTransfer, session);
    }

    /*
     * (non-Javadoc)
     * 
     * @see openr66.context.task.AbstractTask#run()
     */
    @Override
    public void run() {
        logger.info("Reschedule with " + argRule + ":" + argTransfer +
                " and {}", session);
        DbTaskRunner runner = session.getRunner();
        if (runner == null) {
            futureCompletion.setFailure(new OpenR66RunnerErrorException(
                    "No valid runner in Reschedule"));
            return;
        }
        if (runner.isRescheduledTransfer()) {
            // Already rescheduled so ignore
            R66Result result = new R66Result(session, false, ErrorCode.Warning,
                    runner);
            futureCompletion.setResult(result);
            logger.warn("Transfer already Rescheduled: "+runner.toShortString());
            futureCompletion.setSuccess();
            return;
        }
        if (runner.isSelfRequested()) {
            // Self Requested Request so reschedule is ignored
            R66Result result = new R66Result(session, false, ErrorCode.LoopSelfRequestedHost,
                    runner);
            futureCompletion.setResult(result);
            futureCompletion.setFailure(new OpenR66RunnerErrorException(
                "No valid runner in Reschedule since Self Requested"));
            return;
        }
        String finalname = argRule;
        finalname = getReplacedValue(finalname, argTransfer.split(" "));
        String[] args = finalname.split(" ");
        if (args.length < 4) {
            R66Result result = new R66Result(session, false, ErrorCode.Warning,
                    runner);
            futureCompletion.setResult(result);
            logger.warn("Not enough argument in Reschedule: "+runner.toShortString());
            futureCompletion.setSuccess();
            return;
        }
        if (!validateArgs(args)) {
            R66Result result = new R66Result(session, false, ErrorCode.Warning,
                    runner);
            futureCompletion.setResult(result);
            logger.warn("Reschedule unallowed due to argument: "+runner.toShortString());
            futureCompletion.setSuccess();
            return;
        }
        Timestamp start = new Timestamp(newdate);
        try {
            runner.setStart(start);
        } catch (OpenR66RunnerErrorException e) {
            logger.error(
                    "Prepare transfer in\n    FAILURE\n     " +
                            runner.toShortString() + "\n    <AT>" +
                            (new Date(newdate)).toString() + "</AT>", e);
            futureCompletion.setFailure(new OpenR66RunnerErrorException(
                    "Reschedule failed: " + e.getMessage(), e));
            return;
        }
        runner.setRescheduledTransfer();
        R66Result result = new R66Result(session, false, ErrorCode.Warning,
                runner);
        futureCompletion.setResult(result);
        logger.warn("Reschedule transfer in\n    SUCCESS\n    " +
                runner.toShortString() + "\n    <AT>" +
                (new Date(newdate)).toString() + "</AT>");
        futureCompletion.setSuccess();
    }

    protected boolean validateArgs(String[] args) {
        boolean validCode = false;
        for (int i = 0; i < args.length; i ++) {
            if (args[i].equalsIgnoreCase("-delay")) {
                i ++;
                try {
                    newdate = Long.parseLong(args[i]);
                } catch (NumberFormatException e) {
                    logger.warn("Bad Long format: args[i]");
                    return false;
                }
            } else if (args[i].equalsIgnoreCase("-case")) {
                i ++;
                if (!validCode) {
                    String[] codes = args[i].split(",");
                    for (int j = 0; j < codes.length; j ++) {
                        ErrorCode code = ErrorCode.getFromCode(codes[j]);
                        if (session.getLocalChannelReference().getCurrentCode() == code) {
                            logger.debug("Code valid: "+code);
                            validCode = true;
                        }
                    }
                }
            }
        }
        // now we have new delay plus code
        if (!validCode) {
            logger.warn("No valid Code found");
            return false;
        }
        if (newdate <= 0) {
            logger.warn("Delay is negative: "+newdate);
            return false;
        }
        newdate += System.currentTimeMillis();
        newDate = Calendar.getInstance();
        newDate.setTimeInMillis(newdate);
        boolean betweenTest = false;
        boolean betweenResult = false;
        for (int i = 0; i < args.length; i ++) {
            if (args[i].equalsIgnoreCase("-notbetween")) {
                i ++;
                String[] elmts = args[i].split(";");
                boolean startModified = false;
                String[] values = elmts[0].split(":");
                Calendar start = getCalendar(values);
                if (start != null) {
                    startModified = true;
                } else {
                    start = Calendar.getInstance();
                }
                boolean stopModified = false;
                values = elmts[1].split(":");
                Calendar stop = getCalendar(values);
                if (stop != null) {
                    stopModified = true;
                } else {
                    stop = Calendar.getInstance();
                }
                logger.debug("Dates before check: Not between "+start.getTime()+" and "+stop.getTime());
                // Check that start < stop
                if (start.compareTo(stop) > 0) {
                    // no so add 24H to stop
                    stop.add(Calendar.DAY_OF_MONTH, 1);
                }
                // Check that start and stop > newDate (only start is checked since start <= stop)
                if (start.compareTo(newDate) < 0) {
                    start.add(Calendar.DAY_OF_MONTH, 1);
                    stop.add(Calendar.DAY_OF_MONTH, 1);
                }
                logger.debug("Dates after check: Not between "+start.getTime()+" and "+stop.getTime());
                if (!startModified) {
                    if (newDate.compareTo(stop) < 0) {
                        logger.debug("newDate: "+newDate.getTime()+" Should not be between "+start.getTime()+" and "+stop.getTime());
                        return false;
                    }
                } else if (start.compareTo(newDate) < 0) {
                    if ((!stopModified) || (newDate.compareTo(stop) < 0)) {
                        logger.debug("newDate: "+newDate.getTime()+" Should not be between "+start.getTime()+" and "+stop.getTime());
                        return false;
                    }
                }
            } else if (args[i].equalsIgnoreCase("-between")) {
                i ++;
                betweenTest = true;
                String[] elmts = args[i].split(";");
                boolean startModified = false;
                String[] values = elmts[0].split(":");
                Calendar start = getCalendar(values);
                if (start != null) {
                    startModified = true;
                } else {
                    start = Calendar.getInstance();
                }
                boolean stopModified = false;
                values = elmts[1].split(":");
                Calendar stop = getCalendar(values);
                if (stop != null) {
                    stopModified = true;
                } else {
                    stop = Calendar.getInstance();
                }
                logger.debug("Dates before check: Between "+start.getTime()+" and "+stop.getTime());
                // Check that start < stop
                if (start.compareTo(stop) > 0) {
                    // no so add 24H to stop
                    stop.add(Calendar.DAY_OF_MONTH, 1);
                }
                // Check that start and stop > newDate (only start is checked since start <= stop)
                if (start.compareTo(newDate) < 0) {
                    start.add(Calendar.DAY_OF_MONTH, 1);
                    stop.add(Calendar.DAY_OF_MONTH, 1);
                }
                logger.debug("Dates before check: Between "+start.getTime()+" and "+stop.getTime());
                if (!startModified) {
                    if (newDate.compareTo(stop) < 0) {
                        logger.debug("newDate: "+newDate.getTime()+" is between "+start.getTime()+" and "+stop.getTime());
                        betweenResult = true;
                    }
                } else if (start.compareTo(newDate) < 0) {
                    if ((!stopModified) || (newDate.compareTo(stop) < 0)) {
                        logger.debug("newDate: "+newDate.getTime()+" is between "+start.getTime()+" and "+stop.getTime());
                        betweenResult = true;
                    }
                }
            }
        }
        if (betweenTest) {
            logger.debug("Since between is specified, do we found newDate: "+newDate.getTime()+" Result: "+betweenResult);
            return betweenResult;
        }
        logger.debug("newDate: "+newDate.getTime()+" rescheduled");
        return true;
    }

    /**
     * 
     * @param values
     *            as X+n or X-n or X=n or Xn where X=Y/M/D/H/m/s, n=number and
     *            +/- meaning adding/subtracting from current date and = meaning
     *            specific set value
     * @return the Calendar if any specification, or null if no calendar
     *         specified
     */
    private Calendar getCalendar(String[] values) {
        Calendar newcal = Calendar.getInstance();
        boolean isModified = false;
        for (int j = 0; j < values.length; j ++) {
            if (values[j].length() > 1) {
                int addvalue = 0; // will be different of 0
                int value = -1; // will be >= 0
                switch (values[j].charAt(0)) {
                    case '+':
                        try {
                            addvalue = Integer.parseInt(values[j].substring(2));
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        break;
                    case '-':
                        try {
                            addvalue = Integer.parseInt(values[j].substring(1));
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        break;
                    case '=':
                        try {
                            value = Integer.parseInt(values[j].substring(2));
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        break;
                    default: // no sign
                        try {
                            value = Integer.parseInt(values[j].substring(1));
                        } catch (NumberFormatException e) {
                            continue;
                        }
                }
                switch (values[j].charAt(0)) {
                    case 'Y':
                        if (value >= 0) {
                            newcal.set(Calendar.YEAR, value);
                        } else {
                            newcal.add(Calendar.YEAR, addvalue);
                        }
                        isModified = true;
                        break;
                    case 'M':
                        if (value >= 0) {
                            newcal.set(Calendar.MONTH, value);
                        } else {
                            newcal.add(Calendar.MONTH, addvalue);
                        }
                        isModified = true;
                        break;
                    case 'D':
                        if (value >= 0) {
                            newcal.set(Calendar.DAY_OF_MONTH, value);
                        } else {
                            newcal.add(Calendar.DAY_OF_MONTH, addvalue);
                        }
                        isModified = true;
                        break;
                    case 'H':
                        if (value >= 0) {
                            newcal.set(Calendar.HOUR_OF_DAY, value);
                        } else {
                            newcal.add(Calendar.HOUR_OF_DAY, addvalue);
                        }
                        isModified = true;
                        break;
                    case 'm':
                        if (value >= 0) {
                            newcal.set(Calendar.MINUTE, value);
                        } else {
                            newcal.add(Calendar.MINUTE, addvalue);
                        }
                        isModified = true;
                        break;
                    case 'S':
                        if (value >= 0) {
                            newcal.set(Calendar.SECOND, value);
                        } else {
                            newcal.add(Calendar.SECOND, addvalue);
                        }
                        isModified = true;
                        break;
                }
            }
        }
        if (isModified) return newcal;
        return null;
    }
}
