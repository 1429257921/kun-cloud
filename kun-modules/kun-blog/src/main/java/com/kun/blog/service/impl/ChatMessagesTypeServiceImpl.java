package com.kun.blog.service.impl;

import com.kun.blog.entity.po.ChatMessagesType;
import com.kun.blog.mapper.ChatMessagesTypeMapper;
import com.kun.blog.service.IChatMessagesTypeService;
import com.kun.common.database.service.impl.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author gzc
 * @since 2022-10-29 10:01:42
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true, rollbackFor = Exception.class)
public class ChatMessagesTypeServiceImpl extends BaseServiceImpl<ChatMessagesTypeMapper, ChatMessagesType> implements IChatMessagesTypeService {


}
