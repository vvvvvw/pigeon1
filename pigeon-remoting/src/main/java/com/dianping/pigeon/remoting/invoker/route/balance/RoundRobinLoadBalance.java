package com.dianping.pigeon.remoting.invoker.route.balance;

import java.util.List;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;

public class RoundRobinLoadBalance extends AbstractLoadBalance {

	private static final Logger logger = LoggerLoader.getLogger(RoundRobinLoadBalance.class);
	public static final String NAME = "roundRobin";
	public static final LoadBalance instance = new RoundRobinLoadBalance();
	private static int lastSelected = -1;
	private static int currentWeight = 0;

	/**
	 * 
	 */
	@Override
	protected Client doSelect(List<Client> clients, InvokerConfig<?> invokerConfig, InvocationRequest request,
			int[] weights) {
		assert (clients != null && clients.size() > 1);

		int[] _weights = new int[weights.length - 1];
		for (int i = 0; i < weights.length - 1; i++) {
			_weights[i] = weights[i];
		}
		int clientId = roundRobin(_weights);
		//如果clientId小于0，从clients中随机选择出一个，否则，返回clients.get(clientId)
		Client client = clientId < 0 ? clients.get(random.nextInt(_weights.length)) : clients.get(clientId);
		if (logger.isDebugEnabled()) {
			logger.debug("select address:" + client.getAddress());
		}
		return client;
	}

	public int roundRobin(int[] weights) {
		int clientSize = weights.length;
		int gcdWeights = gcdWeights(weights);
		int maxWeight = maxWeight(weights);

		if (lastSelected >= clientSize) {
			lastSelected = clientSize - 1;
		}
		//从上一个lastSelected开始，将currentWeight循环减少权重的公约数
		//如果weights[lastSelected] < currentWeight,lastSelected递增，currentWeight递减，直到weights[lastSelected] >= currentWeight
		while (true) {
			lastSelected = (lastSelected + 1) % clientSize;
			if (lastSelected == 0) {
				currentWeight = currentWeight - gcdWeights;
				if (currentWeight <= 0) {
					currentWeight = maxWeight;
					if (currentWeight == 0) {
						return -1; //如果公约数减到0，随机选择一个client
					}
				}
			}
			if (weights[lastSelected] >= currentWeight) {
				return lastSelected;
			}
		}
	}

	//返回最大权重
	private int maxWeight(int[] weights) {
		int max = weights[0];
		for (int it : weights) {
			if (it > max) {
				max = it;
			}
		}
		return max;
	}

	//求所有weights的公约数
	private int gcdWeights(int[] weights) {
		return gcdN(weights, weights.length);
	}

	//求公约数
	private int gcd(int a, int b) {
		if (0 == b) {
			return a;
		} else {
			return gcd(b, a % b);
		}
	}

	//求digits中所有数的公约数
	public int gcdN(int[] digits, int length) {
		if (1 == length) {
			return digits[0];
		} else {
			return gcd(digits[length - 1], gcdN(digits, length - 1));
		}
	}

}
