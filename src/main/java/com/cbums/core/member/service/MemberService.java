package com.cbums.core.member.service;

import com.cbums.common.exceptions.AccessException;
import com.cbums.common.exceptions.AuthException;
import com.cbums.common.exceptions.EntityNotFoundException;
import com.cbums.common.exceptions.ErrorCode;
import com.cbums.core.member.domain.Member;
import com.cbums.core.member.domain.MemberRepository;
import com.cbums.core.member.domain.UserRoleType;
import com.cbums.core.member.dto.*;
import com.cbums.model.SecurityUser;
import com.cbums.service.EncryptionService;
import com.cbums.common.util.NaverMailSendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class MemberService implements UserDetailsService {
    private final MemberRepository memberRepository;
    private final HttpServletRequest request;
    private final NaverMailSendService naverMailSendService;
    private final EncryptionService encryptionService;

    @Transactional
    public Long registerMember(SignUpRequest signUpRequest) {

        checkExistMember(signUpRequest);

        Member member = buildMember(signUpRequest);
        Member result = memberRepository.save(member);
        //Session 역할을 Controll로 위임해야 TODO
        HttpSession httpSession = request.getSession();
        httpSession.setAttribute("form-writer-id", result.getMemberId());
        return result.getMemberId();
    }

    private void checkExistMember(SignUpRequest signUpRequest) {
        if (memberRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new AuthException(ErrorCode.DUPLICATED_EMAIL);
        }

        if (memberRepository.existsByClassNumber(signUpRequest.getClassNumber())) {
            throw new AuthException(ErrorCode.DUPLICATED_CLASS_NUMBER);
        }
    }

    private Member buildMember(SignUpRequest signUpRequest) {
        return Member.builder()
                .name(signUpRequest.getName())
                .email(signUpRequest.getEmail())
                .department(signUpRequest.getDepartment())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .build();
    }

    @Transactional
    public void addDetails(Member member, MemberAddDetailRequest memberAddDetailRequest) {

        checkDuplicatedNickName(memberAddDetailRequest.getNickName());

        member.setPassword(encryptionService.encode(memberAddDetailRequest.getPassword()));
        member.setIntroduce(memberAddDetailRequest.getIntroduce());
        member.setNickName(memberAddDetailRequest.getNickName());
        member.setProfileImage(memberAddDetailRequest.getProfileImage());
        memberRepository.save(member);
    }

    @Transactional
    public void resign(Member member) {
        member.setResign(true);
        memberRepository.save(member);
    }

    private void checkDuplicatedNickName(String nickName) {
        if (memberRepository.existsByNickName(nickName)) {
            throw new AuthException(ErrorCode.DUPLICATED_NICK_NAME);
        }
    }

    @Transactional
    public void UpdateMember(Member member, UpdateMemberRequest updateMemberRequest) {
        checkDuplicatedNickName(updateMemberRequest.getNickName());

        if (updateMemberRequest.getPassword() != null) {
            member.setPassword(encryptionService.encode(updateMemberRequest.getPassword()));
        }
        member.setDepartment(updateMemberRequest.getDepartment());
        member.setNickName(updateMemberRequest.getNickName());
        member.setProfileImage(updateMemberRequest.getProfileImage());
        member.setIntroduce(updateMemberRequest.getIntroduce());
        member.setPhoneNumber(updateMemberRequest.getPhoneNumber());
        memberRepository.save(member);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("유저를 찾을 수 없습니다. email: " + email));
        SecurityUser securityUser = new SecurityUser();
        securityUser.setUsername(member.getEmail());
        securityUser.setPassword(member.getPassword());

        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        switch (member.getUserRoleType().name()) {
            case "ADMIN":
                grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            case "MEMBER":
                grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
            default:
                grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_VISITED"));
                break;
        }

        securityUser.setAuthorities(grantedAuthorities);
        return securityUser;
    }

    @Transactional
    public void setTemporaryPassword(String email) throws MessagingException {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("유저를 찾을 수 없습니다. email: " + email));

        if (member.getUserRoleType() == UserRoleType.VISITANT) {
            throw new AccessException(ErrorCode.USER_UNAUTHORIZED);
        }

        String temporaryPassword = getRandomStr();
         naverMailSendService.sendEmail(
               member.getEmail(),
                "프로그래밍 동아리 씨부엉 임시 비밀번호입니다",
                "임시 비밀번호는 " + temporaryPassword + " 입니다."
        );

    }

    private String getRandomStr() {

        final int LEFT_LIMIT = 48; // numeral '0'
        final int RIGHT_LIMIT = 122; // letter 'z'
        final int TARGET_STRING_LENGTH = 7;

        Random random = new Random();
        return random.ints(LEFT_LIMIT, RIGHT_LIMIT + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(TARGET_STRING_LENGTH)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    @Transactional(readOnly = true)
    public String getBlindMemberEmail(String classNumber) {
        char[] memberEmail = memberRepository
                .findByClassNumber(classNumber)
                .orElseThrow(() -> new UsernameNotFoundException("유저를 찾을 수 없습니다. classNumber: " + classNumber))
                .getEmail()
                .toCharArray();

        for (int i = 1; i < memberEmail.length; i += 2) {
            memberEmail[i] = '*';
        }

        return memberEmail.toString();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> findAll() {
        List<Member> members = memberRepository.findAll();
        return Collections.unmodifiableList(MemberResponse.listOf(members));
    }

    @Transactional(readOnly = true)
    public MemberResponse findMember(Long memberId) {
        Member member = findById(memberId);
        return MemberResponse.of(member);
    }

    private Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NOT_FOUNDED_ID));
    }

    @Transactional
    public void delete(Long memberId) {
        memberRepository.deleteById(memberId);
    }

    @Transactional
    public void updateRole(Member member, UpdateRoleTypeRequest updateRoleTypeRequest) {
        member.setUserRoleType(updateRoleTypeRequest.getUserRoleType());
        memberRepository.save(member);
    }




}
