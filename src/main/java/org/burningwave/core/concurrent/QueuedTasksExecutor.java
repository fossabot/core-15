/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.concurrent;

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.burningwave.core.Component;

public class QueuedTasksExecutor implements Component {
	private Collection<TaskAbst<?>> tasksQueue;
	private TaskAbst<?> currentTask;
	private Boolean supended;
	private Mutex.Manager mutexManager;
	Thread executor;
	private int defaultPriority;
	private long executedTasksCount;
	private boolean isDaemon;
	private String name;
	private Boolean terminated;
	private Runnable initializer;
	
	private QueuedTasksExecutor(String name, int defaultPriority, boolean isDaemon) {
		mutexManager = Mutex.Manager.create(this);
		tasksQueue = new CopyOnWriteArrayList<>();
		initializer = () -> {
			this.name = name;
			this.defaultPriority = defaultPriority;
			this.isDaemon = isDaemon;
			init0();
		};
		init();
	}
	
	void init() {
		initializer.run();
	}
	
	void init0() {		
		supended = Boolean.FALSE;
		terminated = Boolean.FALSE;
		executedTasksCount = 0;
		executor = new Thread(() -> {
			while (!terminated) {
				if (!tasksQueue.isEmpty()) {
					Iterator<TaskAbst<?>> taskIterator = tasksQueue.iterator();
					while (taskIterator.hasNext()) {
						synchronized(mutexManager.getMutex("resumeCaller")) {
							try {
								if (supended) {
									mutexManager.getMutex("resumeCaller").wait();
									break;
								}
							} catch (InterruptedException exc) {
								logWarn("Exception occurred", exc);
							}
						}
						TaskAbst<?> task =	this.currentTask = taskIterator.next();
						tasksQueue.remove(task);
						int currentExecutablePriority = currentTask.getPriority();
						if (executor.getPriority() != currentExecutablePriority) {
							executor.setPriority(currentExecutablePriority);
						}
						try {
							this.currentTask.execute();							
						} catch (Throwable exc) {
							logError("Exception occurred while executing " + task.toString(), exc);
						}
						++executedTasksCount;
						if (executedTasksCount % 10000 == 0) {
							logInfo("Executed {} tasks", executedTasksCount);
						}
						synchronized(mutexManager.getMutex("suspensionCaller")) {
							mutexManager.getMutex("suspensionCaller").notifyAll();
						}
						if (terminated) {
							break;
						}
					}
				} else {
					synchronized(mutexManager.getMutex("executingFinishedWaiter")) {
						mutexManager.getMutex("executingFinishedWaiter").notifyAll();
					}
					synchronized(mutexManager.getMutex("executableCollectionFiller")) {
						if (tasksQueue.isEmpty()) {
							try {
								mutexManager.getMutex("executableCollectionFiller").wait();
							} catch (InterruptedException exc) {
								logWarn("Exception occurred", exc);
							}
						}
					}
				}
			}
		}, name);
		executor.setPriority(this.defaultPriority);
		executor.setDaemon(isDaemon);
		executor.start();
	}
	
	public static QueuedTasksExecutor create(String name, int initialPriority) {
		return create(name, initialPriority, false, false);
	}
	
	public static QueuedTasksExecutor create(String name, int initialPriority, boolean daemon, boolean undestroyable) {
		if (undestroyable) {
			String creatorClass = Thread.currentThread().getStackTrace()[2].getClassName();
			return new QueuedTasksExecutor(name, initialPriority, daemon) {
				
				@Override
				public void shutDown(boolean waitForTasksTermination) {
					super.shutDown(waitForTasksTermination);
				}
				
				@Override
				void closeResources() {
					this.executor = null;
					if (!Thread.currentThread().getStackTrace()[4].getClassName().equals(creatorClass)) {	
						init();
					} else {
						super.closeResources();
					}
				}
				
			};
		} else {
			return new QueuedTasksExecutor(name, initialPriority, daemon);
		}
	}
	
	public <T> ProducerTask<T> addWithCurrentThreadPriority(Supplier<T> executable) {
		return add(executable, Thread.currentThread().getPriority());
	}
	
	public <T> ProducerTask<T> add(Supplier<T> executable) {
		return add(executable, this.defaultPriority);
	}
	
	public <T> ProducerTask<T> add(Supplier<T> executable, int priority) {
		ProducerTask<T> task = new ProducerTask<T>(executable, priority, this.executor);
		tasksQueue.add(task);
		return add(task);
	}
	
	public Task addWithCurrentThreadPriority(Runnable executable) {
		return add(executable, Thread.currentThread().getPriority());
	}
	
	public Task add(Runnable executable) {
		return add(executable, this.defaultPriority);
	}
	
	public Task add(Runnable executable, int priority) {
		Task task = new Task(executable, priority, this.executor);
		tasksQueue.add(task);
		return add(task);
	}

	<E, T extends TaskAbst<E>> T add(T task) {
		try {
			synchronized(mutexManager.getMutex("executableCollectionFiller")) {
				mutexManager.getMutex("executableCollectionFiller").notifyAll();
			}
		} catch (Throwable exc) {
			logWarn("Exception occurred", exc);
		}
		return task;
	}
	
	public QueuedTasksExecutor waitForExecutablesEnding() {
		return waitForTasksEnding(Thread.currentThread().getPriority());
	}
	
	public QueuedTasksExecutor waitForTasksEnding(int priority) {
		executor.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.changePriority(priority));
		synchronized(mutexManager.getMutex("executingFinishedWaiter")) {
			if (!tasksQueue.isEmpty()) {
				try {
					mutexManager.getMutex("executingFinishedWaiter").wait();
				} catch (InterruptedException exc) {
					logWarn("Exception occurred", exc);
				}
			}
		}
		executor.setPriority(this.defaultPriority);
		return this;
	}
	
	public QueuedTasksExecutor changePriority(int priority) {
		this.defaultPriority = priority;
		executor.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.changePriority(priority));
		return this;
	}
	
	public QueuedTasksExecutor suspend() {
		return suspend(true);
	}
	
	public QueuedTasksExecutor suspend(boolean immediately) {
		return suspend0(immediately, Thread.currentThread().getPriority());
	}
	
	public QueuedTasksExecutor suspend(boolean immediately, int priority) {
		return suspend0(immediately, priority);
	}
	
	QueuedTasksExecutor suspend0(boolean immediately, int priority) {
		executor.setPriority(priority);
		if (immediately) {
			supended = Boolean.TRUE;
			if (!currentTask.hasFinished) {
				synchronized (mutexManager.getMutex("suspensionCaller")) {
					if (!currentTask.hasFinished) {
						try {
							mutexManager.getMutex("suspensionCaller").wait();
						} catch (InterruptedException exc) {
							logWarn("Exception occurred", exc);
						}
					}
				}
			}
		} else {
			add(() -> supended = Boolean.TRUE, priority).join();
		}
		return this;
	}

	public QueuedTasksExecutor resume() {
		synchronized(mutexManager.getMutex("resumeCaller")) {
			try {
				supended = Boolean.FALSE;
				mutexManager.getMutex("resumeCaller").notifyAll();
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
		}	
		return this;
	}
	
	public boolean isSuspended() {
		return supended;
	}
	
	public void shutDown(boolean waitForTasksTermination) {
		Collection<TaskAbst<?>> executables = this.tasksQueue;
		Thread executor = this.executor;
		if (waitForTasksTermination) {
			addWithCurrentThreadPriority(() -> {
				this.terminated = Boolean.TRUE;
				logInfo("Unexecuted tasks {}", executables.size());
				executables.clear();
			});
		} else {
			suspend();
			this.terminated = Boolean.TRUE;
			logInfo("Unexecuted tasks {}", executables.size());
			executables.clear();
			resume();
			try {
				synchronized(mutexManager.getMutex("executableCollectionFiller")) {
					mutexManager.getMutex("executableCollectionFiller").notifyAll();
				}
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}	
		}
		try {
			executor.join();
			closeResources();			
		} catch (InterruptedException exc) {
			logError("Exception occurred", exc);
		}
	}
	
	@Override
	public void close() {
		shutDown(true);
	}
	
	void closeResources() {
		this.executor = null;
		mutexManager.clear();
		mutexManager = null;
		tasksQueue = null;
		currentTask = null;
		initializer = null;
		terminated = null;
		supended = null;
		name = null;
	}
	
	static abstract class TaskAbst<E> {
		E executable;
		boolean hasFinished;
		int priority;
		Thread queuedTasksExecutorThread;
		
		TaskAbst(int priority, Thread queuedTasksExecutorThread) {
			this.queuedTasksExecutorThread = queuedTasksExecutorThread;
			this.priority = priority;
		}
		
		abstract void execute();
		
		public boolean hasFinished() {
			return hasFinished;
		}
		
		void join0(boolean ignoreThread) {
			if (!hasFinished() && ((ignoreThread) ||
				(!ignoreThread && Thread.currentThread() != queuedTasksExecutorThread && queuedTasksExecutorThread != null))
			) {
				synchronized (this) {
					if (!hasFinished() && ((ignoreThread) ||
						(!ignoreThread && Thread.currentThread() != queuedTasksExecutorThread && queuedTasksExecutorThread != null))) {
						try {
							wait();
						} catch (InterruptedException exc) {
							throw Throwables.toRuntimeException(exc);
						}
					}
				}
			}
		}
		
		void markHasFinished() {
			hasFinished = true;
		}
		
		public void changePriority(int priority) {
			this.priority = priority;
		}
		
		public int getPriority() {
			return priority;
		}
	}
	
	public static class Task extends TaskAbst<Runnable> {
		
		Task(Runnable executable, int priority, Thread queuedTasksExecutorThread) {
			super(priority, queuedTasksExecutorThread);
			this.executable = executable;
		}

		@Override
		void execute() {
			executable.run();
			markHasFinished();
			executable = null;
			queuedTasksExecutorThread = null;
			synchronized (this) {
				notifyAll();
			}
		}
		
		public void join(boolean ignoreThread) {
			join0(ignoreThread);
		}
		
		public void join() {
			join0(false);
		}
		
	}
	
	public static class ProducerTask<T> extends TaskAbst<Supplier<T>> {
		private T result;
		
		ProducerTask(Supplier<T> executable, int priority, Thread queuedTasksExecutorThread) {
			super(priority, queuedTasksExecutorThread);
			this.executable = executable;
		}		
		
		@Override
		void execute() {
			result = executable.get();
			markHasFinished();
			executable = null;
			queuedTasksExecutorThread = null;
			synchronized (this) {
				notifyAll();
			}
		}
		
		public T join() {
			return join(false);
		}
		
		public T join(boolean ignoreThread) {
			join0(ignoreThread);
			return result;
		}
		
		public T get() {
			return result;
		}
	}
}