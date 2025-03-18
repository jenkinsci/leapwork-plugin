package com.Leapwork.Leapwork_plugin;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.Leapwork.Leapwork_plugin.model.Failure;
import com.Leapwork.Leapwork_plugin.model.InvalidSchedule;
import com.Leapwork.Leapwork_plugin.model.LeapworkRun;
import com.Leapwork.Leapwork_plugin.model.RunCollection;
import com.Leapwork.Leapwork_plugin.model.RunItem;
import com.Leapwork.Leapwork_plugin.model.LeapworkExecution;
import com.Leapwork.Leapwork_plugin.model.ZephyrScaleResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PluginHandler {

	private static PluginHandler pluginHandler = null;

	private static final String scheduleSeparatorRegex = "\r\n|\n|\\s+,\\s+|,\\s+|\\s+,|,";
	private static final String variableSeparatorRegex = "\\s+:\\s+|:\\s+|\\s+:|:";
	private static final String STRING_EMPTY = "";

	private PluginHandler() {
	}

	public synchronized static PluginHandler getInstance() {
		if (pluginHandler == null) {
			pluginHandler = new PluginHandler();
		}

		return pluginHandler;
	}

	public String getScheduleVariablesRequestPart(String rawScheduleVariables, TaskListener listener) {
		if (Utils.isBlank(rawScheduleVariables))
			return STRING_EMPTY;

		LinkedHashMap<String, String> variables = new LinkedHashMap<>();
		String[] rawSplittedKeyValuePairs = rawScheduleVariables.split(scheduleSeparatorRegex);
		for (String rawKeyValuePair : rawSplittedKeyValuePairs) {
			String[] splittedKeyAndValue = rawKeyValuePair.split(variableSeparatorRegex);
			if (splittedKeyAndValue.length < 2) {
				listener.getLogger().println(String.format(Messages.INVALID_SCHEDULE_VARIABLE, rawKeyValuePair));
				continue;
			}
			String key = splittedKeyAndValue[0];
			String value = splittedKeyAndValue[1];
			if (Utils.isBlank(key) || Utils.isBlank(value)) {
				listener.getLogger().println(String.format(Messages.INVALID_SCHEDULE_VARIABLE, rawKeyValuePair));
				continue;
			}
			if (Utils.tryAddToMap(variables, key, value) == false) {
				listener.getLogger().println(String.format(Messages.SCHEDULE_VARIABLE_KEY_DUPLICATE, rawKeyValuePair));
				continue;
			}
		}
		if (variables.isEmpty())
			return STRING_EMPTY;
		String prefix = "?";
		StringBuilder stringBuilder = new StringBuilder();
		for (Map.Entry<String, String> variable : variables.entrySet()) {
			stringBuilder.append(prefix).append(variable.getKey()).append("=").append(variable.getValue());
			prefix = "&";
		}

		String variableRequestPart = stringBuilder.toString();
		listener.getLogger().println(String.format(Messages.SCHEDULE_VARIABLE_REQUEST_PART, variableRequestPart));
		return variableRequestPart;
	}

	public ArrayList<String> getRawScheduleList(String rawScheduleIds, String rawScheduleTitles) {
		ArrayList<String> rawScheduleList = new ArrayList<>();

		String[] schidsArray = rawScheduleIds.split(scheduleSeparatorRegex);
		String[] testsArray = rawScheduleTitles.split(scheduleSeparatorRegex);

		rawScheduleList.addAll(Arrays.asList(schidsArray));
		rawScheduleList.addAll(Arrays.asList(testsArray));
		rawScheduleList.removeIf(sch -> sch.trim().length() == 0);

		return rawScheduleList;
	}

	public int getTimeDelay(String rawTimeDelay, TaskListener listener) {
		int defaultTimeDelay = 5;
		try {
			if (!rawTimeDelay.isEmpty() || !"".equals(rawTimeDelay))
				return Integer.parseInt(rawTimeDelay);
			else {
				listener.getLogger()
						.println(String.format(Messages.TIME_DELAY_NUMBER_IS_INVALID, rawTimeDelay, defaultTimeDelay));
				return defaultTimeDelay;
			}
		} catch (Exception e) {
			listener.getLogger()
					.println(String.format(Messages.TIME_DELAY_NUMBER_IS_INVALID, rawTimeDelay, defaultTimeDelay));
			return defaultTimeDelay;
		}
	}

	public int getTimeout(String rawTimeout, TaskListener listener) {
		int defaultTimeout = 300;
		try {
			if (!rawTimeout.isEmpty() || !"".equals(rawTimeout))
				return Integer.parseInt(rawTimeout);
			else {
				listener.getLogger()
						.println(String.format(Messages.TIMEOUT_NUMBER_IS_INVALID, rawTimeout, defaultTimeout));
				return defaultTimeout;
			}
		} catch (Exception e) {
			listener.getLogger()
					.println(String.format(Messages.TIMEOUT_NUMBER_IS_INVALID, rawTimeout, defaultTimeout));
			return defaultTimeout;
		}
	}

	public boolean isDoneStatusAsSuccess(String doneStatusAs) {
		return doneStatusAs.contentEquals("Success");
	}

	public String getControllerApiHttpAdderess(String hostname, String rawPort, boolean enableHttps,
			TaskListener listener) {
		StringBuilder stringBuilder = new StringBuilder();
		int port = getPortNumber(rawPort, enableHttps, listener);
		if (enableHttps)
			stringBuilder.append("https://").append(hostname).append(":").append(port);
		else
			stringBuilder.append("http://").append(hostname).append(":").append(port);
		return stringBuilder.toString();
	}

	private int getPortNumber(String rawPortStr, boolean enableHttps, TaskListener listener) {
		int defaultPortNumber;
		int defaultHttpPortNumber = 9001;
		int defaultHttpsPortNumber = 9002;
		try {
			if (!rawPortStr.isEmpty() || !"".equals(rawPortStr))
				return Integer.parseInt(rawPortStr);
			else {
				
				if(enableHttps) {
					defaultPortNumber = defaultHttpsPortNumber;
					listener.getLogger().println(String.format(Messages.PORT_NUMBER_IS_INVALID, defaultPortNumber));
					
				}
				else { 
					defaultPortNumber = defaultHttpPortNumber;
					listener.getLogger().println(String.format(Messages.PORT_NUMBER_IS_INVALID, defaultPortNumber));
					}
				return defaultPortNumber;
			}
		} catch (Exception e) {
			if(enableHttps) {
				defaultPortNumber = defaultHttpsPortNumber;
				listener.getLogger().println(String.format(Messages.PORT_NUMBER_IS_INVALID, defaultPortNumber));
				
			}
			else { 
				defaultPortNumber = defaultHttpPortNumber;
				listener.getLogger().println(String.format(Messages.PORT_NUMBER_IS_INVALID, defaultPortNumber));
				}
			return defaultPortNumber;
		}
	}

	public String getWorkSpaceSafe(FilePath workspace, EnvVars env) {
		try {
			return workspace.toURI().getPath();
		} catch (Exception e) {
			return env.get(Messages.JENKINS_WORKSPACE_VARIABLE);
		}
	}

	public LinkedHashMap<UUID, String> getSchedulesIdTitleHashMap(AsyncHttpClient client, String accessKey,
			String controllerApiHttpAddress, ArrayList<String> rawScheduleList, TaskListener listener,
			ArrayList<InvalidSchedule> invalidSchedules) throws Exception {

		LinkedHashMap<UUID, String> schedulesIdTitleHashMap = new LinkedHashMap<>();

		String scheduleListUri = String.format(Messages.GET_ALL_AVAILABLE_SCHEDULES_URI, controllerApiHttpAddress);

		try {
			Response response = client.prepareGet(scheduleListUri).setHeader("AccessKey", accessKey).execute().get();

			switch (response.getStatusCode()) {
			case 200:
				JsonParser parser = new JsonParser();
				JsonArray jsonScheduleList = parser.parse(response.getResponseBody()).getAsJsonArray();

				for (String rawSchedule : rawScheduleList) {
					boolean successfullyMapped = false;
					for (JsonElement jsonScheduleElement : jsonScheduleList) {
						JsonObject jsonSchedule = jsonScheduleElement.getAsJsonObject();

						UUID Id = Utils.defaultUuidIfNull(jsonSchedule.get("Id"), UUID.randomUUID());
						String Title = Utils.defaultStringIfNull(jsonSchedule.get("Title"), "null Title");

						boolean isEnabled = Utils.defaultBooleanIfNull(jsonSchedule.get("IsEnabled"), false);

						if (Id.toString().contentEquals(rawSchedule)) {
							if (!schedulesIdTitleHashMap.containsValue(Title)) {
								if (isEnabled) {
									schedulesIdTitleHashMap.put(Id, Title);
									listener.getLogger()
											.println(String.format(Messages.SCHEDULE_DETECTED, Title, rawSchedule));
								} else {
									invalidSchedules.add(new InvalidSchedule(rawSchedule,
											String.format(Messages.SCHEDULE_DISABLED, Title, Id)));
									listener.getLogger().println(String.format(Messages.SCHEDULE_DISABLED, Title, Id));

								}
							}

							successfullyMapped = true;
						}

						if (Title.contentEquals(rawSchedule)) {
							if (!schedulesIdTitleHashMap.containsKey(Id)) {
								if (isEnabled) {
									schedulesIdTitleHashMap.put(Id, rawSchedule);
									listener.getLogger()
											.println(String.format(Messages.SCHEDULE_DETECTED, rawSchedule, Id));
								} else {
									invalidSchedules.add(new InvalidSchedule(rawSchedule,
											String.format(Messages.SCHEDULE_DISABLED, Title, Id)));
								}
							}

							successfullyMapped = true;
						}
					}

					if (!successfullyMapped)
						invalidSchedules.add(new InvalidSchedule(rawSchedule, Messages.NO_SUCH_SCHEDULE));
				}
				break;

			case 401:
				StringBuilder errorMessage401 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage401, Messages.INVALID_ACCESS_KEY);
				OnFailedToGetScheduleTitleIdMap(null, errorMessage401.toString(), listener);
				break;
			case 500:
				StringBuilder errorMessage500 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage500, Messages.CONTROLLER_RESPONDED_WITH_ERRORS);
				OnFailedToGetScheduleTitleIdMap(null, errorMessage500.toString(), listener);
				break;
			default:
				StringBuilder errorMessage = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				OnFailedToGetScheduleTitleIdMap(null, errorMessage.toString(), listener);
			}
		} catch (ConnectException | UnknownHostException e) {
			String connectionErrorMessage = String.format(Messages.COULD_NOT_CONNECT_TO, e.getMessage());
			OnFailedToGetScheduleTitleIdMap(e, connectionErrorMessage, listener);
		} catch (InterruptedException e) {
			String interruptedExceptionMessage = String.format(Messages.INTERRUPTED_EXCEPTION, e.getMessage());
			OnFailedToGetScheduleTitleIdMap(e, interruptedExceptionMessage, listener);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof ConnectException || e.getCause() instanceof UnknownHostException) {
				String connectionErrorMessage = String.format(Messages.COULD_NOT_CONNECT_TO, e.getCause().getMessage());
				OnFailedToGetScheduleTitleIdMap(e, connectionErrorMessage, listener);
			} else {
				String executionExceptionMessage = String.format(Messages.EXECUTION_EXCEPTION, e.getMessage());
				OnFailedToGetScheduleTitleIdMap(e, executionExceptionMessage, listener);
			}
		} catch (IOException e) {
			String ioExceptionMessage = String.format(Messages.IO_EXCEPTION, e.getMessage());
			OnFailedToGetScheduleTitleIdMap(e, ioExceptionMessage, listener);
		}

		return schedulesIdTitleHashMap;
	}

	private static HashMap<UUID, String> OnFailedToGetScheduleTitleIdMap(Exception e, String errorMessage,
			TaskListener listener) throws Exception {
		listener.error(Messages.SCHEDULE_TITLE_OR_ID_ARE_NOT_GOT);
		if (errorMessage != null && errorMessage.isEmpty() == false)
			listener.error(errorMessage);
		else
			errorMessage = Messages.SCHEDULE_TITLE_OR_ID_ARE_NOT_GOT;
		if (e == null)
			e = new Exception(errorMessage);
		throw e;
	}

	public UUID runSchedule(AsyncHttpClient client, String controllerApiHttpAddress, String accessKey, UUID scheduleId,
			String scheduleTitle, TaskListener listener, LeapworkRun run, String scheduleVariablesRequestPart)
			throws Exception {

		String uri = String.format(Messages.RUN_SCHEDULE_URI, controllerApiHttpAddress, scheduleId.toString(),
				scheduleVariablesRequestPart);

		try {
			Response response = client.preparePut(uri).setHeader("AccessKey", accessKey).setBody("").execute().get();

			switch (response.getStatusCode()) {
			case 200:
				String successMessage = String.format(Messages.SCHEDULE_RUN_SUCCESS, scheduleTitle, scheduleId);
				listener.getLogger().println(Messages.SCHEDULE_CONSOLE_LOG_SEPARATOR);
				listener.getLogger().println(successMessage);
				JsonParser parser = new JsonParser();
				JsonObject jsonRunObject = parser.parse(response.getResponseBody()).getAsJsonObject();
				JsonElement jsonRunId = jsonRunObject.get("RunId");
				String runIdStr = Utils.defaultStringIfNull(jsonRunId);
				UUID runId = UUID.fromString(runIdStr);
				return runId;

			case 400:
				StringBuilder errorMessage400 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage400, Messages.INVALID_VARIABLE_KEY_NAME);
				return OnScheduleRunFailure(errorMessage400, run, scheduleId, listener);

			case 401:
				StringBuilder errorMessage401 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage401, Messages.INVALID_ACCESS_KEY);
				return OnScheduleRunFailure(errorMessage401, run, scheduleId, listener);

			case 404:
				StringBuilder errorMessage404 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage404,
						String.format(Messages.NO_SUCH_SCHEDULE_WAS_FOUND, scheduleTitle, scheduleId));
				return OnScheduleRunFailure(errorMessage404, run, scheduleId, listener);

			case 446:
				StringBuilder errorMessage446 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage446, Messages.NO_DISK_SPACE);
				return OnScheduleRunFailure(errorMessage446, run, scheduleId, listener);

			case 455:
				StringBuilder errorMessage455 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage455, Messages.DATABASE_NOT_RESPONDING);
				return OnScheduleRunFailure(errorMessage455, run, scheduleId, listener);

			case 500:
				StringBuilder errorMessage500 = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				appendLine(errorMessage500, Messages.CONTROLLER_RESPONDED_WITH_ERRORS);
				return OnScheduleRunFailure(errorMessage500, run, scheduleId, listener);

			default:
				StringBuilder errorMessage = new StringBuilder(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				return OnScheduleRunFailure(errorMessage, run, scheduleId, listener);
			}
		} catch (ConnectException | UnknownHostException e) {
			OnScheduleRunConnectionFailure(e, listener);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof ConnectException || e.getCause() instanceof UnknownHostException) {
				OnScheduleRunConnectionFailure(e, listener);
			} else
				throw e;
		}
		return null;
	}

	private static UUID OnScheduleRunFailure(StringBuilder errorMessage, LeapworkRun failedRun, UUID scheduleId,
			TaskListener listener) {
		listener.error(
				String.format(Messages.SCHEDULE_RUN_FAILURE, failedRun.getScheduleTitle(), scheduleId.toString()));
		listener.error(errorMessage.toString());
		failedRun.setError(errorMessage.toString());
		failedRun.incErrors();
		return null;
	}

	private static UUID OnScheduleRunConnectionFailure(Exception e, TaskListener listener) {
		listener.error(String.format(Messages.COULD_NOT_CONNECT_TO_BUT_WAIT, e.getCause().getMessage()));
		return null;
	}

	@SuppressFBWarnings(value = "REC_CATCH_EXCEPTION")
	public boolean stopRun(String controllerApiHttpAddress, UUID runId, String scheduleTitle, String accessKey, int timeout,
			final TaskListener listener) {
		boolean isSuccessfullyStopped = false;

		listener.error(String.format(Messages.STOPPING_RUN, scheduleTitle, runId));
		String uri = String.format(Messages.STOP_RUN_URI, controllerApiHttpAddress, runId.toString());

		AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
											.setReadTimeout(timeout * 1000)
											.setRequestTimeout(timeout * 1000)
											.build();

		try (AsyncHttpClient client = new AsyncHttpClient(config)) {

			Response response = client.preparePut(uri).setBody("").setHeader("AccessKey", accessKey).execute().get();
			client.close();

			switch (response.getStatusCode()) {
			case 200:
				JsonParser parser = new JsonParser();
				JsonObject jsonStopRunObject = parser.parse(response.getResponseBody()).getAsJsonObject();
				JsonElement jsonStopSuccessfull = jsonStopRunObject.get("OperationCompleted");
				isSuccessfullyStopped = Utils.defaultBooleanIfNull(jsonStopSuccessfull, false);
				if (isSuccessfullyStopped)
					listener.error(String.format(Messages.STOP_RUN_SUCCESS, scheduleTitle, runId.toString()));
				else
					listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));
				break;

			case 401:
				listener.error(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				listener.error(Messages.INVALID_ACCESS_KEY);
				listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));
				break;
			case 404:
				listener.error(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				listener.error(String.format(Messages.NO_SUCH_RUN_WAS_FOUND, runId, scheduleTitle));
				listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));
				break;
			case 446:
				listener.error(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				listener.error(Messages.NO_DISK_SPACE);
				listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));
				break;
			case 455:
				listener.error(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				listener.error(Messages.DATABASE_NOT_RESPONDING);
				listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));
				break;
			case 500:
				listener.error(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				listener.error(Messages.CONTROLLER_RESPONDED_WITH_ERRORS);
				listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));
				break;
			default:
				listener.error(
						String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
				listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));

			}
		}

		catch (Exception e) {
			listener.error(String.format(Messages.STOP_RUN_FAIL, scheduleTitle, runId.toString()));
			listener.error(e.getMessage());
		} finally {
			return isSuccessfullyStopped;
		}

	}

	public void createJUnitReport(FilePath workspace, String JUnitReportFile, final TaskListener listener,
			RunCollection buildResult) throws Exception {
		try {	
			FilePath reportFile;
			if (workspace.isRemote()) {
				String fileName = "/" + JUnitReportFile;
				
				VirtualChannel channel = workspace.getChannel();
				URI uri = workspace.toURI();
				String workspacePathUrl = Paths.get(Paths.get(uri).toString(), JUnitReportFile).toString();
				reportFile = new FilePath(channel, workspacePathUrl);
				listener.getLogger()
						.println(String.format(Messages.FULL_REPORT_FILE_PATH, reportFile.toURI().getPath()));
			} else {
				File file = new File(workspace.toURI().getPath(), JUnitReportFile);
				listener.getLogger().println(String.format(Messages.FULL_REPORT_FILE_PATH, file.getCanonicalPath()));
				if (!file.exists()) {
					try {
						file.createNewFile();
					} catch (Exception e) {
						throw e;
					}
				}

				reportFile = new FilePath(file);
			}

			try (StringWriter writer = new StringWriter()) {
				JAXBContext context = JAXBContext.newInstance(RunCollection.class);

				Marshaller m = context.createMarshaller();
				m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
				m.marshal(buildResult, writer);

				try (StringWriter formattedWriter = new StringWriter()) {
					formattedWriter.append(writer.getBuffer().toString().replace("&amp;#xA;", "&#xA;"));
					reportFile.write(formattedWriter.toString(), "UTF-8");
				}

			}
		} catch (FileNotFoundException e) {
			listener.error(Messages.REPORT_FILE_NOT_FOUND);
			listener.error(e.getMessage());
			throw new Exception(e);
		} catch (IOException e) {
			listener.error(Messages.REPORT_FILE_CREATION_FAILURE);
			listener.error(e.getMessage());
			throw new Exception(e);
		} catch (JAXBException e) {
			listener.error(Messages.REPORT_FILE_CREATION_FAILURE);
			listener.error(e.getMessage());
			throw new Exception(e);
		}
	}

	public void createZephyrScaleJSONReport(FilePath workspace, String JUnitReportFile, final TaskListener listener,
			RunCollection buildResult) throws Exception {
		try {	
			FilePath reportFile;
			if (workspace.isRemote()) {
				String fileName = "/" + JUnitReportFile;
				
				VirtualChannel channel = workspace.getChannel();
				URI uri = workspace.toURI();
				String workspacePathUrl = Paths.get(Paths.get(uri).toString(), JUnitReportFile).toString();
				reportFile = new FilePath(channel, workspacePathUrl);
				listener.getLogger()
						.println(String.format(Messages.FULL_REPORT_FILE_PATH, reportFile.toURI().getPath()));
			} else {
				File file = new File(workspace.toURI().getPath(), JUnitReportFile);
				listener.getLogger().println(String.format(Messages.FULL_REPORT_FILE_PATH, file.getCanonicalPath()));
				if (!file.exists()) {
					try {
						file.createNewFile();
					} catch (Exception e) {
						throw e;
					}
				}
				reportFile = new FilePath(file);
			}
			List<LeapworkExecution> executions = new ArrayList<>();
			for (LeapworkRun leapworkRun : buildResult.leapworkRuns) {
				for (RunItem runItem : leapworkRun.runItems) {
					LeapworkExecution execution = new LeapworkExecution(
													leapworkRun.getScheduleTitle() + "." + runItem.getCaseName(),
													runItem.getCaseStatus()
												);
					executions.add(execution);
				}
			}
			ZephyrScaleResult zephyrScaleResult = new ZephyrScaleResult(1, executions);
			ObjectMapper objectMapper = new ObjectMapper();
			String jsonString = objectMapper.writeValueAsString(zephyrScaleResult);
			listener.getLogger().println(jsonString);
			try (OutputStream outputStream = reportFile.write()) {
                outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
            }
			listener.getLogger().println("report is generated");
		} catch (FileNotFoundException e) {
			listener.error(Messages.REPORT_FILE_NOT_FOUND);
			listener.error(e.getMessage());
			throw new Exception(e);
		} catch (IOException e) {
			listener.error(Messages.REPORT_FILE_CREATION_FAILURE);
			listener.error(e.getMessage());
			throw new Exception(e);
		}
		catch (Exception e) {
			listener.error(e.getMessage());
			throw new Exception(e);
		} 
	}

	public String getRunStatus(AsyncHttpClient client, String controllerApiHttpAddress, String accessKey, UUID runId)
			throws Exception {

		String uri = String.format(Messages.GET_RUN_STATUS_URI, controllerApiHttpAddress, runId.toString());

		Response response = client.prepareGet(uri).setHeader("AccessKey", accessKey).execute().get();

		switch (response.getStatusCode()) {
		case 200:
			JsonParser parser = new JsonParser();
			JsonObject runStatusObject = parser.parse(response.getResponseBody()).getAsJsonObject();
			JsonElement jsonRunStatus = runStatusObject.get("Status");
			String runStatus = Utils.defaultStringIfNull(jsonRunStatus, "Queued");
			return runStatus;

		case 401:
			StringBuilder errorMessage401 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage401, Messages.INVALID_ACCESS_KEY);
			throw new Exception(errorMessage401.toString());

		case 404:
			StringBuilder errorMessage404 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage404, String.format(Messages.NO_SUCH_RUN, runId));
			throw new Exception(errorMessage404.toString());

		case 455:
			StringBuilder errorMessage455 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage455, Messages.DATABASE_NOT_RESPONDING);
			throw new Exception(errorMessage455.toString());

		case 500:
			StringBuilder errorMessage500 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage500, Messages.CONTROLLER_RESPONDED_WITH_ERRORS);
			throw new Exception(errorMessage500.toString());

		default:
			String errorMessage = String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(),
					response.getStatusText());
			throw new Exception(errorMessage);
		}
	}

	public List<UUID> getRunRunItems(AsyncHttpClient client, String controllerApiHttpAddress, String accessKey,
			UUID runId) throws Exception {
		String uri = String.format(Messages.GET_RUN_ITEMS_IDS_URI, controllerApiHttpAddress, runId.toString());

		Response response = client.prepareGet(uri).setHeader("AccessKey", accessKey).execute().get();

		switch (response.getStatusCode()) {
		case 200:

			JsonParser parser = new JsonParser();
			JsonObject jsonRunItemsObject = parser.parse(response.getResponseBody()).getAsJsonObject();
			JsonElement jsonRunItemsElement = jsonRunItemsObject.get("RunItemIds");

			List<UUID> runItems = new ArrayList<>();

			if (jsonRunItemsElement != null) {
				JsonArray jsonRunItems = jsonRunItemsElement.getAsJsonArray();
				for (int i = 0; i < jsonRunItems.size(); i++) {
					UUID runItemId = UUID.fromString(jsonRunItems.get(i).getAsString());
					runItems.add(runItemId);
				}
			}

			return runItems;

		case 401:
			StringBuilder errorMessage401 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage401, Messages.INVALID_ACCESS_KEY);
			throw new Exception(errorMessage401.toString());

		case 404:
			StringBuilder errorMessage404 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage404, String.format(Messages.NO_SUCH_RUN, runId));
			throw new Exception(errorMessage404.toString());

		case 446:
			StringBuilder errorMessage446 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage446, Messages.NO_DISK_SPACE);
			throw new Exception(errorMessage446.toString());

		case 455:
			StringBuilder errorMessage455 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage455, Messages.DATABASE_NOT_RESPONDING);
			throw new Exception(errorMessage455.toString());

		case 500:
			StringBuilder errorMessage500 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage500, Messages.CONTROLLER_RESPONDED_WITH_ERRORS);
			throw new Exception(errorMessage500.toString());

		default:
			String errorMessage = String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(),
					response.getStatusText());
			throw new Exception(errorMessage);

		}
	}

	@SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE")
	public RunItem getRunItem(AsyncHttpClient client, String controllerApiHttpAddress, String accessKey, UUID runItemId,
			String scheduleTitle, boolean doneStatusAsSuccess, boolean writePassedKeyframes,
			final TaskListener listener) throws Exception {

		String uri = String.format(Messages.GET_RUN_ITEM_URI, controllerApiHttpAddress, runItemId.toString());

		Response response = client.prepareGet(uri).setHeader("AccessKey", accessKey).execute().get();

		switch (response.getStatusCode()) {
		case 200:

			JsonParser parser = new JsonParser();
			JsonObject jsonRunItem = parser.parse(response.getResponseBody()).getAsJsonObject();

			// FlowInfo
			JsonElement jsonFlowInfo = jsonRunItem.get("FlowInfo");
			JsonObject flowInfo = jsonFlowInfo.getAsJsonObject();
			JsonElement jsonFlowId = flowInfo.get("FlowId");
			UUID flowId = Utils.defaultUuidIfNull(jsonFlowId, UUID.randomUUID());
			JsonElement jsonFlowTitle = flowInfo.get("FlowTitle");
			String flowTitle = Utils.defaultStringIfNull(jsonFlowTitle);
			JsonElement jsonFlowStatus = flowInfo.get("Status");
			String flowStatus = Utils.defaultStringIfNull(jsonFlowStatus, "NoStatus");

			// AgentInfo
			JsonElement jsonAgentInfo = jsonRunItem.get("AgentInfo");
			JsonObject AgentInfo = jsonAgentInfo.getAsJsonObject();
			JsonElement jsonAgentId = AgentInfo.get("AgentId");
			UUID agentId = Utils.defaultUuidIfNull(jsonAgentId, UUID.randomUUID());
			JsonElement jsonAgentTitle = AgentInfo.get("AgentTitle");
			String agentTitle = Utils.defaultStringIfNull(jsonAgentTitle);
			JsonElement jsonAgentConnectionType = AgentInfo.get("ConnectionType");
			String agentConnectionType = Utils.defaultStringIfNull(jsonAgentConnectionType, "Not defined");

			JsonElement jsonRunId = jsonRunItem.get("AutomationRunId");
			UUID runId = Utils.defaultUuidIfNull(jsonRunId, UUID.randomUUID());

			String elapsed = Utils.defaultElapsedIfNull(jsonRunItem.get("Elapsed"));
			double milliseconds = Utils.defaultDoubleIfNull(jsonRunItem.get("ElapsedSeconds"), 0);

			RunItem runItem = new RunItem(flowTitle, flowStatus, milliseconds, scheduleTitle);

			if (flowStatus.contentEquals("Initializing") || flowStatus.contentEquals("Connecting")
					|| flowStatus.contentEquals("Connected") || flowStatus.contentEquals("Running")
					|| flowStatus.contentEquals("IsProcessing") || flowStatus.contentEquals("NoStatus")
					|| (flowStatus.contentEquals("Passed") && !writePassedKeyframes)
					|| (flowStatus.contentEquals("Done") && doneStatusAsSuccess && !writePassedKeyframes)) {
				return runItem;
			} else {
				Failure keyframes = getRunItemKeyFrames(client, controllerApiHttpAddress, accessKey, runItemId, runItem,
						scheduleTitle, agentTitle, listener);
				runItem.failure = keyframes;
				return runItem;
			}

		case 401:
			StringBuilder errorMessage401 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage401, Messages.INVALID_ACCESS_KEY);
			throw new Exception(errorMessage401.toString());

		case 404:
			StringBuilder errorMessage404 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage404, String.format(Messages.NO_SUCH_RUN_ITEM_WAS_FOUND, runItemId, scheduleTitle));
			throw new Exception(errorMessage404.toString());

		case 446:
			StringBuilder errorMessage446 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage446, Messages.NO_DISK_SPACE);
			throw new Exception(errorMessage446.toString());

		case 455:
			StringBuilder errorMessage455 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage455, Messages.DATABASE_NOT_RESPONDING);
			throw new Exception(errorMessage455.toString());

		case 500:
			StringBuilder errorMessage500 = new StringBuilder(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			appendLine(errorMessage500, Messages.CONTROLLER_RESPONDED_WITH_ERRORS);
			throw new Exception(errorMessage500.toString());

		default:
			String errorMessage = String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(),
					response.getStatusText());
			throw new Exception(errorMessage);
		}
	}

	public Failure getRunItemKeyFrames(AsyncHttpClient client, String controllerApiHttpAddress, String accessKey,
			UUID runItemId, RunItem runItem, String scheduleTitle, String agentTitle, final TaskListener listener)
			throws Exception {

		String uri = String.format(Messages.GET_RUN_ITEM_KEYFRAMES_URI, controllerApiHttpAddress, runItemId.toString());

		Response response = client.prepareGet(uri).setHeader("AccessKey", accessKey).execute().get();

		switch (response.getStatusCode()) {
		case 200:

			JsonArray jsonKeyframes = TryParseKeyframeJson(response.getResponseBody(), listener);

			if (jsonKeyframes != null) {
				listener.getLogger().println(Messages.CASE_CONSOLE_LOG_SEPARATOR);
				listener.getLogger().println(String.format(Messages.CASE_INFORMATION, runItem.getCaseName(),
						runItem.getCaseStatus(), runItem.getElapsedTime()));
				StringBuilder fullKeyframes = new StringBuilder("");

				for (JsonElement jsonKeyFrame : jsonKeyframes) {
					
					String level = Utils.defaultStringIfNull(jsonKeyFrame.getAsJsonObject().get("Level"), "Trace");
					if (!level.contentEquals("") && !level.contentEquals("Trace")) {
						String keyFrameTimeStamp = jsonKeyFrame.getAsJsonObject().get("Timestamp").getAsJsonObject()
								.get("Value").getAsString();
						String keyFrameLogMessage = jsonKeyFrame.getAsJsonObject().get("LogMessage").getAsString();
						JsonElement keyFrameBlockTitle = jsonKeyFrame.getAsJsonObject().get("BlockTitle");
						String keyFrame = "";
						if (keyFrameBlockTitle != null) {
							keyFrame = String.format(Messages.CASE_STACKTRACE_FORMAT_BLOCKTITLE, keyFrameTimeStamp,
									keyFrameBlockTitle.getAsString(), keyFrameLogMessage);
						} else {
							keyFrame = String.format(Messages.CASE_STACKTRACE_FORMAT, keyFrameTimeStamp,
									keyFrameLogMessage);
						}
						listener.getLogger().println(keyFrame);
						fullKeyframes.append(keyFrame);
						fullKeyframes.append("&#xA;");
					}
				}

				fullKeyframes.append("AgentTitle: ").append(agentTitle).append("&#xA;");
				listener.getLogger().println("AgentTitle: " + agentTitle);
				fullKeyframes.append("Schedule: ").append(scheduleTitle);
				listener.getLogger().println("Schedule: " + scheduleTitle);

				return new Failure(fullKeyframes.toString());
			} else {
				listener.getLogger().println(Messages.FAILED_TO_PARSE_RESPONSE_KEYFRAME_JSON_ARRAY);
				return new Failure(Messages.FAILED_TO_PARSE_RESPONSE_KEYFRAME_JSON_ARRAY);
			}

		case 401:
			listener.error(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			listener.error(Messages.INVALID_ACCESS_KEY);
			break;
		case 404:
			listener.error(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			listener.error(String.format(Messages.NO_SUCH_RUN_ITEM_WAS_FOUND, runItemId, scheduleTitle));
			break;

		case 446:
			listener.error(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			listener.error(Messages.NO_DISK_SPACE);
			break;

		case 455:
			listener.error(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			listener.error(Messages.DATABASE_NOT_RESPONDING);
			break;

		case 500:
			listener.error(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			listener.error(Messages.CONTROLLER_RESPONDED_WITH_ERRORS);
			break;

		default:
			listener.error(
					String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText()));
			break;
		}
		return null;
	}

	private static JsonArray TryParseKeyframeJson(String response, TaskListener listener) {
		JsonParser parser = new JsonParser();
		try {
			JsonArray jsonKeyframes = parser.parse(response).getAsJsonArray();
			return jsonKeyframes;
		} catch (Exception e) {
			listener.error(e.getMessage());
			return null;
		}
	}

	private void appendLine(StringBuilder stringBuilder, String line) {
		if (stringBuilder != null) {
			stringBuilder.append(System.getProperty("line.separator"));
			stringBuilder.append(line);
		}
	}

	public String getReportFileName(String rawReportName, String rawReportExtension, String defaultXmlReportName, String defaultJsonReportName) {
		if(Utils.isBlank(rawReportName))
			return rawReportExtension.equalsIgnoreCase("json") ? defaultJsonReportName : defaultXmlReportName;
		int reportFileExtensionIndex = rawReportName.lastIndexOf('.');
		if (reportFileExtensionIndex == -1)
			return rawReportName + "." + rawReportExtension;

		String reportFileExtension = rawReportName.substring(reportFileExtensionIndex + 1);
		String baseFileName = rawReportName.substring(0, reportFileExtensionIndex);
		if(reportFileExtension.equalsIgnoreCase("json") && rawReportExtension.equalsIgnoreCase("xml")){
			return baseFileName + ".xml";
		}
		else if(reportFileExtension.equalsIgnoreCase("xml") && rawReportExtension.equalsIgnoreCase("json")){
			return baseFileName + ".json";
		}
		else if(reportFileExtension.equalsIgnoreCase(rawReportExtension)){
			return rawReportName;
		}
		else{
			return rawReportName + "." + rawReportExtension;
		} 
	}
}
