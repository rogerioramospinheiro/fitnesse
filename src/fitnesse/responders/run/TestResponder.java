// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.responders.run;

import fitnesse.authentication.SecureOperation;
import fitnesse.authentication.SecureResponder;
import fitnesse.authentication.SecureTestOperation;
import fitnesse.responders.ChunkingResponder;
import fitnesse.wiki.PageData;
import fitnesse.wiki.WikiPage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

public class TestResponder extends ChunkingResponder implements SecureResponder {
  private static LinkedList<TestEventListener> eventListeners = new LinkedList<TestEventListener>();
  protected PageData data;
  protected CompositeFormatter formatter;
  private boolean isClosed = false;

  private boolean fastTest = false;
  private boolean remoteDebug = false;
  protected TestSystem testSystem;

  public TestResponder() {
    super();
    formatter = new CompositeFormatter();
  }

  protected void doSending() throws Exception {
    fastTest |= request.hasInput("debug");
    remoteDebug |= request.hasInput("remote_debug");
    data = page.getData();

    createFormatterAndWriteHead();

    sendPreTestNotification();

    performExecution();

    formatter.allTestingComplete();
  }

  protected void createFormatterAndWriteHead() throws Exception {
    if (response.isXmlFormat()) {    //todo temporal coupling.  HistoryFormatter must be first.   Yuk..
      formatter.add(createTestHistoryFormatter());
      formatter.add(createXmlFormatter());
    } else {
      formatter.add(createTestHistoryFormatter());
      formatter.add(createHtmlFormatter());
    }

    formatter.writeHead(getTitle());
  }

  String getTitle() {
    return "Test Results";
  }

  BaseFormatter createXmlFormatter() throws Exception {
    BaseFormatter formatter = new XmlFormatter(context, page) {
      @Override
      protected void close() throws Exception {
        closeHtmlResponse();
      }

      @Override
      protected void writeData(byte[] byteArray) throws Exception {
        response.add(byteArray);
      }
    };
    return formatter;
  }


  BaseFormatter createHtmlFormatter() throws Exception {
    BaseFormatter formatter = new TestHtmlFormatter(context, page, context.htmlPageFactory) {
      @Override
      protected void writeData(String output) throws Exception {
        addToResponse(output);
      }

      @Override
      protected void close() throws Exception {
        closeHtmlResponse(exitCode());
      }
    };
    return formatter;
  }

  protected XmlFormatter createTestHistoryFormatter() throws Exception {
    return new XmlFormatter(context, page) {
      @Override
      public void setExecutionLogAndTrackingId(String stopResponderId, CompositeExecutionLog log) throws Exception {
      }

      protected void close() throws Exception {
      }

      protected void writeData(byte[] byteArray) throws Exception {
      }

      protected void writeResults() throws Exception {
        File resultPath = new File(String.format("%s/%s/%s",
          context.getTestHistoryDirectory(),
          page.getPageCrawler().getFullPath(page).toString(),
          makeResultFileName(getFinalSummary())));
        File resultDirectory = new File(resultPath.getParent());
        resultDirectory.mkdirs();
        File resultFile = new File(resultDirectory, resultPath.getName());
        final FileWriter fileWriter = new FileWriter(resultFile);
        Writer writer = new Writer() {
          public void write(char[] cbuf, int off, int len) {
            String fragment = new String(cbuf, off, len);
            try {
              if (fileWriter != null)
                fileWriter.append(fragment);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }

          public void flush() throws IOException {
          }

          public void close() throws IOException {
          }
        };
        writeResults(writer);

        if (fileWriter != null)
          fileWriter.close();
      }

    };
  }

  protected void sendPreTestNotification() throws Exception {
    for (TestEventListener eventListener : eventListeners) {
      eventListener.notifyPreTest(this, data);
    }
  }

  protected void performExecution() throws Exception {
    List<WikiPage> test2run = new SuiteContentsFinder(page, root, null).makePageListForSingleTest();

    MultipleTestsRunner runner = new MultipleTestsRunner(test2run, context, page, formatter);
    runner.setFastTest(fastTest);
    runner.setDebug(isRemoteDebug());

    if (isEmpty(page))
      formatter.addMessageForBlankHtml();

    runner.executeTestPages();
  }

  private boolean isEmpty(WikiPage page) throws Exception {
    return page.getData().getContent().length() == 0;
  }

  public SecureOperation getSecureOperation() {
    return new SecureTestOperation();
  }


  public static void registerListener(TestEventListener listener) {
    eventListeners.add(listener);
  }

  public void setFastTest(boolean fastTest) {
    this.fastTest = fastTest;
  }

  public boolean isFastTest() {
    return fastTest;
  }

  public void addToResponse(byte[] output) throws Exception {
    if (!isClosed()) {
      response.add(output);
    }
  }

  public void addToResponse(String output) throws Exception {
    if (!isClosed()) {
      response.add(output);
    }
  }

  synchronized boolean isClosed() {
    return isClosed;
  }

  synchronized void setClosed() {
    isClosed = true;
  }

  void closeHtmlResponse(int exitCode) throws Exception {
    if (!isClosed()) {
      setClosed();
      response.closeChunks();
      response.addTrailingHeader("Exit-Code", String.valueOf(exitCode));
      response.closeTrailer();
      response.close();
    }
  }

  void closeHtmlResponse() throws Exception {
    if (!isClosed()) {
      setClosed();
      response.closeChunks();
      response.close();
    }
  }

  boolean isRemoteDebug() {
    return remoteDebug;
  }
}
